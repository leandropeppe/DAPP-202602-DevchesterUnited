package com.devchesterunited.devchesterunitedv2.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketTokenIssuanceService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class MarketTokenIssuanceIntegrationTest extends PostgresTestSupport {
    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 10, 15, 12, 0, 0, 0, ZoneOffset.UTC);

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MarketTokenIssuanceService emissions;

    private UUID playerId;
    private UUID counterpartyId;
    private boolean counterpartyCreated;

    @AfterEach
    void cleanup() {
        if (playerId != null) {
            jdbc.update("DELETE FROM market_token_issuances WHERE player_id = ?", playerId);
            jdbc.update("DELETE FROM jugadores WHERE id = ?", playerId);
        }
        if (counterpartyId != null && counterpartyCreated) {
            jdbc.update("DELETE FROM market_counterparties WHERE id = ?", counterpartyId);
        }
    }

    @Test
    void emisionEsExactaEIdempotenteAunqueSeReintente() {
        fixture(true);

        emissions.emitirEnPrimeraActivacion(playerId, counterpartyId);
        emissions.emitirEnPrimeraActivacion(playerId, counterpartyId);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM market_token_issuances WHERE player_id = ?",
                Long.class, playerId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT issued_quantity FROM market_token_issuances WHERE player_id = ?",
                Long.class, playerId)).isEqualTo(100L);
        assertThat(jdbc.queryForObject(
                "SELECT inventory_quantity FROM market_token_issuances WHERE player_id = ?",
                Long.class, playerId)).isEqualTo(100L);
    }

    @Test
    void dosActivacionesConcurrentesConservanUnaSolaEmision() throws Exception {
        fixture(true);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> invokeConcurrently(start));
            var second = pool.submit(() -> invokeConcurrently(start));
            start.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM market_token_issuances WHERE player_id = ?",
                Long.class, playerId)).isEqualTo(1L);
    }

    private void invokeConcurrently(CountDownLatch start) {
        try {
            start.await(10, TimeUnit.SECONDS);
            emissions.emitirEnPrimeraActivacion(playerId, counterpartyId);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private void fixture(boolean active) {
        playerId = UUID.randomUUID();
        UUID teamId = jdbc.queryForObject("SELECT id FROM equipos ORDER BY id LIMIT 1", UUID.class);
        var existing = jdbc.query("SELECT id FROM market_counterparties "
                        + "WHERE counterparty_type = 'SUPERUSER' LIMIT 1",
                (rs, row) -> rs.getObject(1, UUID.class));
        counterpartyCreated = existing.isEmpty();
        counterpartyId = counterpartyCreated ? UUID.randomUUID() : existing.get(0);
        if (counterpartyCreated) {
            jdbc.update("INSERT INTO market_counterparties "
                    + "(id, counterparty_type, display_name, created_at) VALUES (?, 'SUPERUSER', ?, ?)",
                    counterpartyId, "T022 Superuser", NOW);
        }
        jdbc.update("INSERT INTO jugadores "
                + "(id, name, position, jersey_number, nationality, team_id, is_active, "
                + "created_at, updated_at) VALUES (?, 'T022 Player', 'Forward', 9, 'Argentina', ?, ?, ?, ?)",
                playerId, teamId, active, NOW, NOW);
    }
}
