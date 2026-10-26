package com.devchesterunited.devchesterunitedv2.integration.persistence;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPurchaseRequest;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketPurchaseService;
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

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifica la integración de compra con la finalización durable de rechazos. */
@SpringBootTest(properties = "token-market.initialization.auto-start=false")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MarketPurchaseRejectionIntegrationTest {
    private static final Timestamp NOW = Timestamp.from(Instant.parse("2026-10-19T12:00:00Z"));
    private static final String PASSWORD_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoO5V5f3n0e0D8n8f2h5x0M5K2Qf7tQm2S";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MarketPurchaseService purchases;

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("devchester_purchase_rejection_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

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
                        + "SET status = 'READY', updated_at = EXCLUDED.updated_at", NOW, NOW);
        jdbc.update("INSERT INTO usuarios (id, username, email, password_hash, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)", userId, "t062purchase" + userId,
                userId + "@example.test", PASSWORD_HASH, NOW, NOW);
        jdbc.update("INSERT INTO jugadores (id, name, position, jersey_number, nationality, "
                        + "team_id, is_active, created_at, updated_at) VALUES (?, 'T062 Player', "
                        + "'Forward', 9, 'Argentina', ?, TRUE, ?, ?)", playerId, teamId, NOW, NOW);
        jdbc.update("INSERT INTO market_credit_accounts "
                        + "(id, user_id, counterparty_id, balance, created_at, updated_at) "
                        + "VALUES (?, ?, NULL, 100.00, ?, ?)", UUID.randomUUID(), userId, NOW, NOW);
        jdbc.update("INSERT INTO market_credit_accounts "
                        + "(id, user_id, counterparty_id, balance, created_at, updated_at) "
                        + "VALUES (?, NULL, ?, 0.00, ?, ?) ON CONFLICT DO NOTHING",
                UUID.randomUUID(), counterpartyId, NOW, NOW);
        jdbc.update("INSERT INTO market_token_issuances "
                        + "(player_id, counterparty_id, issued_quantity, inventory_quantity, "
                        + "created_at, updated_at) VALUES (?, ?, 100, 100, ?, ?)",
                playerId, counterpartyId, NOW, NOW);
        jdbc.update("INSERT INTO market_quote_versions "
                        + "(quote_id, version, player_id, price_per_token, published_at, "
                        + "valid_from, validity_status, created_at) VALUES (?, 1, ?, 1.25, ?, ?, 'VALID', ?)",
                quoteId, playerId, NOW, NOW, NOW);
        jdbc.update("INSERT INTO market_quote_headers "
                        + "(player_id, quote_id, current_version, updated_at, validity_status) "
                        + "VALUES (?, ?, 1, ?, 'VALID')", playerId, quoteId, NOW);
    }

    @Test
    void saldoInsuficienteQuedaRejectedAuditaYNoMuta() {
        jdbc.update("UPDATE market_credit_accounts SET balance = 1.00 WHERE user_id = ?", userId);

        assertThatThrownBy(() -> purchases.comprar(request("insufficient-key", 3L)))
                .isInstanceOfSatisfying(TokenMarketException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(
                                TokenMarketException.Reason.INSUFFICIENT_BALANCE));

        assertThat(status("insufficient-key")).isEqualTo("REJECTED");
        assertThat(auditCount()).isOne();
        assertThat(confirmedCount()).isZero();
        assertThat(movementCount()).isZero();
        assertThat(balance()).isEqualByComparingTo("1.00");
    }

    @Test
    void reintentoRejectedNoDuplicaAuditoria() {
        jdbc.update("UPDATE market_credit_accounts SET balance = 1.00 WHERE user_id = ?", userId);
        MarketPurchaseRequest request = request("retry-key", 3L);

        assertThatThrownBy(() -> purchases.comprar(request)).isInstanceOf(TokenMarketException.class);
        var retry = purchases.comprar(request);

        assertThat(retry.estado()).isEqualTo(MarketIdempotencyStatus.REJECTED);
        assertThat(auditCount()).isOne();
    }

    @Test
    void compraConfirmadaNoGeneraAuditoria() {
        var result = purchases.comprar(request("confirmed-key", 2L));

        assertThat(result.estado()).isEqualTo(MarketIdempotencyStatus.CONFIRMED);
        assertThat(auditCount()).isZero();
        assertThat(confirmedCount()).isOne();
    }

    private MarketPurchaseRequest request(String key, long quantity) {
        return new MarketPurchaseRequest(userId, key, playerId, quantity, quoteId, 1L);
    }

    private UUID findOrCreateSuperuser() {
        var ids = jdbc.query("SELECT id FROM market_counterparties "
                        + "WHERE counterparty_type = 'SUPERUSER' LIMIT 1",
                (rs, rowNum) -> rs.getObject(1, UUID.class));
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO market_counterparties "
                        + "(id, counterparty_type, display_name, created_at) "
                        + "VALUES (?, 'SUPERUSER', 'T062 Superuser', ?)", id, NOW);
        jdbc.update("INSERT INTO market_credit_accounts "
                        + "(id, user_id, counterparty_id, balance, created_at, updated_at) "
                        + "VALUES (?, NULL, ?, 0.00, ?, ?)", UUID.randomUUID(), id, NOW, NOW);
        return id;
    }

    private String status(String key) {
        return jdbc.queryForObject("SELECT status FROM market_idempotency_requests "
                        + "WHERE user_id = ? AND idempotency_key = ?", String.class, userId, key);
    }

    private long auditCount() {
        return jdbc.queryForObject("SELECT count(*) FROM market_rejected_attempts WHERE user_id = ?",
                Long.class, userId);
    }

    private long confirmedCount() {
        return jdbc.queryForObject("SELECT count(*) FROM market_confirmed_transactions WHERE user_id = ?",
                Long.class, userId);
    }

    private long movementCount() {
        return jdbc.queryForObject("SELECT count(*) FROM market_movements movement JOIN "
                        + "market_confirmed_transactions transaction_record "
                        + "ON transaction_record.id = movement.transaction_id "
                        + "WHERE transaction_record.user_id = ?", Long.class, userId);
    }

    private BigDecimal balance() {
        return jdbc.queryForObject("SELECT balance FROM market_credit_accounts WHERE user_id = ?",
                BigDecimal.class, userId);
    }
}
