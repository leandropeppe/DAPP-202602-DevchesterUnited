package com.devchesterunited.devchesterunitedv2.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketSaleRequest;
import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketSaleService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Verifica la integración de ventas con la finalización durable de rechazos. */
@SpringBootTest(properties = "token-market.initialization.auto-start=false")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MarketSaleRejectionIntegrationTest {
    private static final Timestamp NOW = Timestamp.from(Instant.parse("2026-10-19T12:00:00Z"));
    private static final String PASSWORD_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoO5V5f3n0e0D8n8f2h5x0M5K2Qf7tQm2S";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("devchester_sale_rejection_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MarketSaleService sales;

    private UUID userId;
    private UUID playerId;
    private UUID quoteId;

    @BeforeEach
    void fixture() {
        userId = UUID.randomUUID();
        playerId = UUID.randomUUID();
        quoteId = UUID.randomUUID();
        UUID counterpartyId = findOrCreateSuperuser();
        UUID teamId = jdbc.queryForObject("SELECT id FROM equipos ORDER BY id LIMIT 1", UUID.class);

        jdbc.update("INSERT INTO market_state (id, status, epoch, created_at, updated_at) "
                + "VALUES (1, 'READY', 2, ?, ?) ON CONFLICT (id) DO UPDATE "
                + "SET status = 'READY', epoch = GREATEST(market_state.epoch, EXCLUDED.epoch), "
                + "failure_reason = NULL, updated_at = EXCLUDED.updated_at", NOW, NOW);
        jdbc.update("INSERT INTO usuarios (id, username, email, password_hash, created_at, "
                + "updated_at) VALUES (?, ?, ?, ?, ?, ?)", userId, "t062sale" + userId,
                userId + "@example.test", PASSWORD_HASH, NOW, NOW);
        jdbc.update("INSERT INTO jugadores (id, name, position, jersey_number, nationality, "
                + "team_id, is_active, created_at, updated_at) VALUES (?, 'T062 Sale Player', "
                + "'Forward', 9, 'Argentina', ?, TRUE, ?, ?)", playerId, teamId, NOW, NOW);
        jdbc.update("INSERT INTO market_credit_accounts "
                + "(id, user_id, counterparty_id, balance, created_at, updated_at) "
                + "VALUES (?, ?, NULL, 0.00, ?, ?)", UUID.randomUUID(), userId, NOW, NOW);
        ensureSuperuserAccount(counterpartyId);
        jdbc.update("INSERT INTO market_token_issuances "
                + "(player_id, counterparty_id, issued_quantity, inventory_quantity, "
                + "created_at, updated_at) VALUES (?, ?, 100, 99, ?, ?)",
                playerId, counterpartyId, NOW, NOW);
        jdbc.update("INSERT INTO market_holdings "
                + "(user_id, player_id, quantity, acquisition_cost, updated_at) "
                + "VALUES (?, ?, 1, 0.50, ?)", userId, playerId, NOW);
        jdbc.update("INSERT INTO market_quote_versions "
                + "(quote_id, version, player_id, price_per_token, published_at, "
                + "valid_from, validity_status, created_at) VALUES (?, 1, ?, 1.25, ?, ?, "
                + "'VALID', ?)", quoteId, playerId, NOW, NOW, NOW);
        jdbc.update("INSERT INTO market_quote_headers "
                + "(player_id, quote_id, current_version, updated_at, validity_status) "
                + "VALUES (?, ?, 1, ?, 'VALID')", playerId, quoteId, NOW);
    }

    @Test
    void tenenciaInsuficienteQuedaRejectedAuditaYNoMuta() {
        assertThatThrownBy(() -> sales.vender(request("insufficient-key", 2L)))
                .isInstanceOfSatisfying(TokenMarketException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(
                                TokenMarketException.Reason.INSUFFICIENT_INVENTORY));

        assertThat(status("insufficient-key")).isEqualTo("REJECTED");
        assertThat(auditCount()).isOne();
        assertThat(confirmedCount()).isZero();
        assertThat(inventory()).isEqualTo(99L);
        assertThat(holding()).isEqualTo(1L);
    }

    @Test
    void reintentoRejectedNoDuplicaAuditoria() {
        MarketSaleRequest request = request("retry-key", 2L);

        assertThatThrownBy(() -> sales.vender(request)).isInstanceOf(TokenMarketException.class);
        var retry = sales.vender(request);

        assertThat(retry.estado()).isEqualTo(MarketIdempotencyStatus.REJECTED);
        assertThat(auditCount()).isOne();
    }

    @Test
    void ventaConfirmadaNoGeneraAuditoria() {
        var result = sales.vender(request("confirmed-key", 1L));

        assertThat(result.estado()).isEqualTo(MarketIdempotencyStatus.CONFIRMED);
        assertThat(auditCount()).isZero();
        assertThat(confirmedCount()).isOne();
        assertThat(inventory()).isEqualTo(100L);
        assertThat(holding()).isZero();
    }

    private MarketSaleRequest request(String key, long quantity) {
        return new MarketSaleRequest(userId, key, playerId, quantity, quoteId, 1L);
    }

    private UUID findOrCreateSuperuser() {
        List<UUID> existing = jdbc.query("SELECT id FROM market_counterparties "
                        + "WHERE counterparty_type = 'SUPERUSER' LIMIT 1",
                (rs, row) -> rs.getObject(1, UUID.class));
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO market_counterparties "
                + "(id, counterparty_type, display_name, created_at) VALUES (?, 'SUPERUSER', "
                + "'T062 Sale Superuser', ?)", id, NOW);
        return id;
    }

    private void ensureSuperuserAccount(UUID counterpartyId) {
        List<UUID> existing = jdbc.query("SELECT id FROM market_credit_accounts "
                        + "WHERE counterparty_id = ? LIMIT 1",
                (rs, row) -> rs.getObject(1, UUID.class), counterpartyId);
        if (existing.isEmpty()) {
            jdbc.update("INSERT INTO market_credit_accounts "
                    + "(id, user_id, counterparty_id, balance, created_at, updated_at) "
                    + "VALUES (?, NULL, ?, 100.00, ?, ?)", UUID.randomUUID(), counterpartyId,
                    NOW, NOW);
            return;
        }
        jdbc.update("UPDATE market_credit_accounts SET balance = 100.00, updated_at = ? "
                + "WHERE id = ?", NOW, existing.get(0));
    }

    private String status(String key) {
        return jdbc.queryForObject("SELECT status FROM market_idempotency_requests "
                + "WHERE user_id = ? AND idempotency_key = ?", String.class, userId, key);
    }

    private long auditCount() {
        return jdbc.queryForObject("SELECT count(*) FROM market_rejected_attempts "
                + "WHERE user_id = ?", Long.class, userId);
    }

    private long confirmedCount() {
        return jdbc.queryForObject("SELECT count(*) FROM market_confirmed_transactions "
                + "WHERE user_id = ?", Long.class, userId);
    }

    private long inventory() {
        return jdbc.queryForObject("SELECT inventory_quantity FROM market_token_issuances "
                + "WHERE player_id = ?", Long.class, playerId);
    }

    private long holding() {
        return jdbc.queryForObject("SELECT quantity FROM market_holdings "
                + "WHERE user_id = ? AND player_id = ?", Long.class, userId, playerId);
    }
}
