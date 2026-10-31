package com.devchesterunited.devchesterunitedv2.integration.market;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPurchaseRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPurchaseResult;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketPurchaseService;
import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "token-market.initialization.auto-start=false")
@Transactional
class PrimaryPurchaseRegressionIntegrationTest extends PostgresTestSupport {
    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 10, 29, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final String PASSWORD_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoO5V5f3n0e0D8n8f2h5x0M5K2Qf7tQm2S";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MarketPurchaseService purchases;

    private UUID userId;
    private UUID playerId;
    private UUID quoteId;

    @BeforeEach
    void fixture() {
        userId = UUID.randomUUID();
        playerId = UUID.randomUUID();
        quoteId = UUID.randomUUID();
        UUID teamId = jdbc.queryForObject("SELECT id FROM equipos ORDER BY id LIMIT 1",
                UUID.class);
        UUID counterpartyId = findOrCreateSuperuser();
        ensureSuperuserAccount(counterpartyId);

        jdbc.update("INSERT INTO market_state "
                + "(id, status, epoch, created_at, updated_at) VALUES (1, 'READY', 2, ?, ?) "
                + "ON CONFLICT (id) DO UPDATE SET status = 'READY', updated_at = EXCLUDED.updated_at",
                NOW, NOW);
        jdbc.update("INSERT INTO usuarios "
                + "(id, username, email, password_hash, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)", userId,
                "primary" + userId.toString().replace("-", "").substring(0, 20),
                userId + "@example.test", PASSWORD_HASH, NOW, NOW);
        jdbc.update("INSERT INTO jugadores "
                + "(id, name, position, jersey_number, nationality, team_id, is_active, "
                + "created_at, updated_at) VALUES (?, 'Primary Regression Player', 'Forward', 9, "
                + "'Argentina', ?, TRUE, ?, ?)", playerId, teamId, NOW, NOW);
        jdbc.update("INSERT INTO market_credit_accounts "
                + "(id, user_id, counterparty_id, balance, created_at, updated_at) "
                + "VALUES (?, ?, NULL, 100.00, ?, ?)", UUID.randomUUID(), userId, NOW, NOW);
        jdbc.update("INSERT INTO market_token_issuances "
                + "(player_id, counterparty_id, issued_quantity, inventory_quantity, created_at, "
                + "updated_at) VALUES (?, ?, 100, 100, ?, ?)", playerId, counterpartyId, NOW, NOW);
        jdbc.update("INSERT INTO market_quote_versions "
                + "(quote_id, version, player_id, price_per_token, published_at, valid_from, "
                + "validity_status, created_at) VALUES (?, 1, ?, 2.50, ?, ?, 'VALID', ?)",
                quoteId, playerId, NOW, NOW, NOW);
        jdbc.update("INSERT INTO market_quote_headers "
                + "(player_id, quote_id, current_version, updated_at, validity_status) "
                + "VALUES (?, ?, 1, ?, 'VALID')", playerId, quoteId, NOW);
    }

    @Test
    void compraPrimariaMantieneCotizacionTransfiereUnaVezYReproduceIdempotencia() {
        MarketPurchaseRequest request = new MarketPurchaseRequest(
                userId, "primary-regression-key", playerId, 4L, quoteId, 1L);

        MarketPurchaseResult first = purchases.comprar(request);
        MarketPurchaseResult replay = purchases.comprar(request);

        assertThat(first.estado().name()).isEqualTo("CONFIRMED");
        assertThat(replay.estado()).isEqualTo(first.estado());
        assertThat(replay.transaccionId()).isEqualTo(first.transaccionId());
        assertThat(replay.montoTotal()).isEqualByComparingTo("10.00");
        assertThat(balance(userId)).isEqualByComparingTo("90.00");
        assertThat(jdbc.queryForObject("SELECT inventory_quantity FROM market_token_issuances "
                + "WHERE player_id = ?", Long.class, playerId)).isEqualTo(96L);
        assertThat(jdbc.queryForObject("SELECT quantity FROM market_holdings "
                + "WHERE user_id = ? AND player_id = ?", Long.class, userId, playerId))
                .isEqualTo(4L);
        assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM market_holdings "
                + "WHERE user_id = ? AND player_id = ?", Long.class, userId, playerId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_confirmed_transactions "
                + "WHERE user_id = ? AND player_id = ?", Long.class, userId, playerId))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT unit_price FROM market_confirmed_transactions "
                + "WHERE id = ?", BigDecimal.class, first.transaccionId()))
                .isEqualByComparingTo("2.50");
        assertThat(jdbc.queryForObject("SELECT quote_id FROM market_confirmed_transactions "
                + "WHERE id = ?", UUID.class, first.transaccionId())).isEqualTo(quoteId);
        assertThat(jdbc.queryForObject("SELECT quote_version FROM market_confirmed_transactions "
                + "WHERE id = ?", Long.class, first.transaccionId())).isEqualTo(1L);
    }

    private BigDecimal balance(UUID id) {
        return jdbc.queryForObject("SELECT balance FROM market_credit_accounts WHERE user_id = ?",
                BigDecimal.class, id);
    }

    private UUID findOrCreateSuperuser() {
        var ids = jdbc.query("SELECT id FROM market_counterparties "
                        + "WHERE counterparty_type = 'SUPERUSER' ORDER BY id LIMIT 1",
                (rs, row) -> rs.getObject(1, UUID.class));
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO market_counterparties "
                + "(id, counterparty_type, display_name, created_at) "
                + "VALUES (?, 'SUPERUSER', 'Primary Regression Superuser', ?)", id, NOW);
        return id;
    }

    private void ensureSuperuserAccount(UUID counterpartyId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM market_credit_accounts "
                + "WHERE counterparty_id = ?", Long.class, counterpartyId);
        if (count == 0) {
            jdbc.update("INSERT INTO market_credit_accounts "
                    + "(id, user_id, counterparty_id, balance, created_at, updated_at) "
                    + "VALUES (?, NULL, ?, 10000.00, ?, ?)", UUID.randomUUID(), counterpartyId,
                    NOW, NOW);
        } else {
            jdbc.update("UPDATE market_credit_accounts SET balance = 10000.00, updated_at = ? "
                    + "WHERE counterparty_id = ?", NOW, counterpartyId);
        }
    }
}
