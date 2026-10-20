package com.devchesterunited.devchesterunitedv2.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketPurchaseRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketPurchaseResult;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketIdempotencyStatus;
import com.devchesterunited.devchesterunitedv2.service.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.interfaces.MarketPurchaseService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** T046: integración del orquestador dentro de un único límite transaccional. */
@SpringBootTest(properties = "token-market.initialization.auto-start=false")
@Transactional
class MarketPurchaseServiceIntegrationTest extends PostgresTestSupport {
    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 10, 15, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final String PASSWORD_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoO5V5f3n0e0D8n8f2h5x0M5K2Qf7tQm2S";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MarketPurchaseService purchases;
    @PersistenceContext private EntityManager entityManager;

    private UUID userId;
    private UUID playerId;
    private UUID counterpartyId;
    private UUID quoteId;

    @BeforeEach
    void fixture() {
        userId = UUID.randomUUID();
        playerId = UUID.randomUUID();
        quoteId = UUID.randomUUID();
        UUID teamId = jdbc.queryForObject("SELECT id FROM equipos ORDER BY id LIMIT 1", UUID.class);
        counterpartyId = findOrCreateSuperuser();

        jdbc.update("INSERT INTO market_state "
                + "(id, status, epoch, created_at, updated_at) VALUES (1, 'READY', 2, ?, ?) "
                + "ON CONFLICT (id) DO UPDATE SET status = 'READY', "
                + "updated_at = GREATEST(market_state.created_at, EXCLUDED.updated_at)",
                NOW, NOW);
        jdbc.update("INSERT INTO usuarios "
                + "(id, username, email, password_hash, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)", userId,
                "purchase" + userId.toString().replace("-", "").substring(0, 20),
                userId + "@example.test", PASSWORD_HASH, NOW, NOW);
        jdbc.update("INSERT INTO jugadores "
                + "(id, name, position, jersey_number, nationality, team_id, is_active, "
                + "created_at, updated_at) VALUES (?, 'Orchestrated Player', 'Forward', 9, "
                + "'Argentina', ?, TRUE, ?, ?)", playerId, teamId, NOW, NOW);
        jdbc.update("INSERT INTO market_credit_accounts "
                + "(id, user_id, counterparty_id, balance, created_at, updated_at) "
                + "VALUES (?, ?, NULL, 100.00, ?, ?)", UUID.randomUUID(), userId, NOW, NOW);
        jdbc.update("INSERT INTO market_credit_accounts "
                + "(id, user_id, counterparty_id, balance, created_at, updated_at) "
                + "VALUES (?, NULL, ?, 0.00, ?, ?) ON CONFLICT DO NOTHING",
                UUID.randomUUID(), counterpartyId, NOW, NOW);
        jdbc.update("INSERT INTO market_token_issuances "
                + "(player_id, counterparty_id, issued_quantity, inventory_quantity, created_at, "
                + "updated_at) VALUES (?, ?, 100, 100, ?, ?)", playerId, counterpartyId, NOW, NOW);
        jdbc.update("INSERT INTO market_quote_versions "
                + "(quote_id, version, player_id, price_per_token, published_at, valid_from, "
                + "validity_status, created_at) VALUES (?, 1, ?, 1.25, ?, ?, 'VALID', ?)",
                quoteId, playerId, NOW, NOW, NOW);
        jdbc.update("INSERT INTO market_quote_headers "
                + "(player_id, quote_id, current_version, updated_at, validity_status) "
                + "VALUES (?, ?, 1, ?, 'VALID')", playerId, quoteId, NOW);
    }

    @Test
    void compraConfirmaIdempotenciaYMutacionesEnLaMismaTransaccion() {
        MarketPurchaseResult result = purchases.comprar(new MarketPurchaseRequest(
                userId, "orchestrator-key", playerId, 3L, quoteId, 1L));
        entityManager.flush();

        assertThat(result.estado()).isEqualTo(MarketIdempotencyStatus.CONFIRMED);
        assertThat(result.montoTotal()).isEqualByComparingTo("3.75");
        assertThat(jdbc.queryForObject("SELECT balance FROM market_credit_accounts "
                + "WHERE user_id = ?", BigDecimal.class, userId))
                .isEqualByComparingTo("96.25");
        assertThat(jdbc.queryForObject("SELECT inventory_quantity FROM market_token_issuances "
                + "WHERE player_id = ?", Long.class, playerId)).isEqualTo(97L);
        assertThat(jdbc.queryForObject("SELECT status FROM market_idempotency_requests "
                + "WHERE user_id = ? AND idempotency_key = 'orchestrator-key'", String.class, userId))
                .isEqualTo("CONFIRMED");
    }

    @Test
    void saldoInsuficienteNoConfirmaNiMutaLaCuenta() {
        jdbc.update("UPDATE market_credit_accounts SET balance = 1.00 WHERE user_id = ?", userId);

        assertThatThrownBy(() -> purchases.comprar(new MarketPurchaseRequest(
                userId, "rollback-key", playerId, 3L, quoteId, 1L)))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(TokenMarketException.Reason.INSUFFICIENT_BALANCE));
        entityManager.flush();

        assertThat(jdbc.queryForObject("SELECT balance FROM market_credit_accounts "
                + "WHERE user_id = ?", BigDecimal.class, userId))
                .isEqualByComparingTo("1.00");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_confirmed_transactions "
                + "WHERE user_id = ?", Long.class, userId)).isZero();
    }

    private UUID findOrCreateSuperuser() {
        List<UUID> ids = jdbc.query("SELECT id FROM market_counterparties "
                + "WHERE counterparty_type = 'SUPERUSER' LIMIT 1",
                (rs, rowNum) -> rs.getObject(1, UUID.class));
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO market_counterparties "
                + "(id, counterparty_type, display_name, created_at) VALUES (?, 'SUPERUSER', "
                + "'Integration Superuser', ?)", id, NOW);
        return id;
    }
}
