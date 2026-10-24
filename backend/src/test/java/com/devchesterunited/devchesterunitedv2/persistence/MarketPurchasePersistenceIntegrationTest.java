package com.devchesterunited.devchesterunitedv2.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPurchaseSettlement;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketPurchasePersistenceService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@SpringBootTest(properties = "token-market.initialization.auto-start=false")
@Transactional
class MarketPurchasePersistenceIntegrationTest extends PostgresTestSupport {
    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 10, 15, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final String PASSWORD_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoO5V5f3n0e0D8n8f2h5x0M5K2Qf7tQm2S";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MarketPurchasePersistenceService purchases;
    @PersistenceContext private EntityManager entityManager;

    private UUID userId;
    private UUID playerId;
    private UUID counterpartyId;
    private UUID requestId;
    private UUID quoteId;

    @BeforeEach
    void fixture() {
        userId = UUID.randomUUID();
        playerId = UUID.randomUUID();
        requestId = UUID.randomUUID();
        quoteId = UUID.randomUUID();
        UUID teamId = jdbc.queryForObject("SELECT id FROM equipos ORDER BY id LIMIT 1", UUID.class);
        counterpartyId = findOrCreateSuperuser();

        jdbc.update("INSERT INTO usuarios "
                + "(id, username, email, password_hash, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)", userId,
                "purchase" + userId.toString().replace("-", "").substring(0, 20),
                userId + "@example.test", PASSWORD_HASH, NOW, NOW);
        jdbc.update("INSERT INTO jugadores "
                + "(id, name, position, jersey_number, nationality, team_id, is_active, "
                + "created_at, updated_at) VALUES (?, 'Purchase Player', 'Forward', 9, "
                + "'Argentina', ?, TRUE, ?, ?)", playerId, teamId, NOW, NOW);
        jdbc.update("INSERT INTO market_credit_accounts "
                + "(id, user_id, counterparty_id, balance, created_at, updated_at) "
                + "VALUES (?, ?, NULL, 100.00, ?, ?)", UUID.randomUUID(), userId, NOW, NOW);
        jdbc.update("INSERT INTO market_credit_accounts "
                + "(id, user_id, counterparty_id, balance, created_at, updated_at) "
                + "VALUES (?, NULL, ?, 0.00, ?, ?) ON CONFLICT DO NOTHING",
                UUID.randomUUID(), counterpartyId, NOW, NOW);
        jdbc.update("UPDATE market_credit_accounts SET balance = 0.00, "
                + "updated_at = GREATEST(created_at, ?) "
                + "WHERE counterparty_id = ?", NOW, counterpartyId);
        jdbc.update("INSERT INTO market_token_issuances "
                + "(player_id, counterparty_id, issued_quantity, inventory_quantity, created_at, "
                + "updated_at) VALUES (?, ?, 100, 100, ?, ?)", playerId, counterpartyId, NOW, NOW);
        jdbc.update("INSERT INTO market_quote_versions "
                + "(quote_id, version, player_id, price_per_token, published_at, valid_from, "
                + "validity_status, created_at) VALUES (?, 1, ?, 1.25, ?, ?, 'VALID', ?)",
                quoteId, playerId, NOW, NOW, NOW);
        jdbc.update("INSERT INTO market_quote_headers "
                + "(player_id, quote_id, current_version, updated_at) VALUES (?, ?, 1, ?)",
                playerId, quoteId, NOW);
        jdbc.update("INSERT INTO market_idempotency_requests "
                + "(id, user_id, idempotency_key, operation_type, parameters_hash, status, "
                + "execution_token, created_at, updated_at) VALUES (?, ?, 'purchase-integration', "
                + "'PURCHASE', 'hash', 'PROCESSING', ?, ?, ?)",
                requestId, userId, UUID.randomUUID(), NOW, NOW);
    }

    @Test
    void persisteEnPostgresLaTransferenciaLaTenenciaYLosCuatroMovimientos() {
        UUID transactionId = purchases.persistirCompra(new MarketPurchaseSettlement(
                requestId, userId, playerId, "Purchase Player", counterpartyId, quoteId, 1L,
                3L, new BigDecimal("1.25"), new BigDecimal("3.75"), NOW.toInstant()));
        entityManager.flush();

        assertThat(jdbc.queryForObject("SELECT balance FROM market_credit_accounts "
                + "WHERE user_id = ?", BigDecimal.class, userId))
                .isEqualByComparingTo("96.25");
        assertThat(jdbc.queryForObject("SELECT balance FROM market_credit_accounts "
                + "WHERE counterparty_id = ?", BigDecimal.class, counterpartyId))
                .isEqualByComparingTo("3.75");
        assertThat(jdbc.queryForObject("SELECT inventory_quantity FROM market_token_issuances "
                + "WHERE player_id = ?", Long.class, playerId)).isEqualTo(97L);
        assertThat(jdbc.queryForObject("SELECT quantity FROM market_holdings "
                + "WHERE user_id = ? AND player_id = ?", Long.class, userId, playerId))
                .isEqualTo(3L);
        assertThat(jdbc.queryForObject("SELECT acquisition_cost FROM market_holdings "
                + "WHERE user_id = ? AND player_id = ?", BigDecimal.class, userId, playerId))
                .isEqualByComparingTo("3.75000000");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_movements "
                + "WHERE transaction_id = ?", Long.class, transactionId)).isEqualTo(4L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_confirmed_transactions "
                + "WHERE idempotency_request_id = ?", Long.class, requestId)).isEqualTo(1L);
    }

    private UUID findOrCreateSuperuser() {
        var existing = jdbc.query("SELECT id FROM market_counterparties "
                        + "WHERE counterparty_type = 'SUPERUSER' LIMIT 1",
                (rs, row) -> rs.getObject(1, UUID.class));
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO market_counterparties "
                + "(id, counterparty_type, display_name, created_at) VALUES (?, 'SUPERUSER', "
                + "'Mercado', ?)", id, NOW);
        return id;
    }
}
