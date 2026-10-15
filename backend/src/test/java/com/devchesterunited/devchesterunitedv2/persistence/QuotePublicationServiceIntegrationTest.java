package com.devchesterunited.devchesterunitedv2.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.service.dto.QuotePublicationRequest;
import com.devchesterunited.devchesterunitedv2.service.impl.QuotePublicationService;
import java.math.BigDecimal;
import java.time.Instant;
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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class QuotePublicationServiceIntegrationTest extends PostgresTestSupport {
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    private static final OffsetDateTime DB_NOW =
            OffsetDateTime.of(2026, 10, 15, 12, 0, 0, 0, ZoneOffset.UTC);

    @Autowired private JdbcTemplate jdbc;
    @Autowired private QuotePublicationService quotes;
    @Autowired private VersionCotizacionMercadoRepository versions;

    private UUID playerId;

    @AfterEach
    void cleanup() {
        if (playerId != null) {
            jdbc.update("DELETE FROM market_quote_headers WHERE player_id = ?", playerId);
        }
    }

    @Test
    void inicialEsIdempotenteYLaVersionFuturaActualizaSoloLaCabecera() {
        fixture();

        var initial = quotes.publishInitialQuote(playerId, new BigDecimal("1.00"), true);
        var retry = quotes.publishInitialQuote(playerId, new BigDecimal("1.00"), true);
        var future = quotes.publish(new QuotePublicationRequest(
                playerId, new BigDecimal("1.25"), NOW.plusSeconds(60),
                NOW.plusSeconds(60), null, "valuation-v1"));

        assertThat(retry).isEqualTo(initial);
        assertThat(future.quoteId()).isEqualTo(initial.quoteId());
        assertThat(future.version()).isEqualTo(2L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM market_quote_versions WHERE player_id = ?",
                Long.class, playerId)).isEqualTo(2L);
        assertThat(jdbc.queryForObject(
                "SELECT current_version FROM market_quote_headers WHERE player_id = ?",
                Long.class, playerId)).isEqualTo(2L);
        assertThat(quotes.currentQuote(playerId)).get().extracting(v -> v.version())
                .isEqualTo(2L);
        assertThat(versions.findExactVigente(playerId, initial.quoteId(), 1L,
                NOW.plusSeconds(120))).isEmpty();
        assertThat(versions.findExactVigente(playerId, future.quoteId(), 2L,
                NOW.plusSeconds(120))).isPresent();
    }

    @Test
    void versionesPersistidasSonInmutables() {
        fixture();
        var initial = quotes.publishInitialQuote(playerId, new BigDecimal("1.00"), true);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE market_quote_versions SET price_per_token = 2.00 "
                        + "WHERE quote_id = ? AND version = ?",
                initial.quoteId(), initial.version()))
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject(
                "SELECT price_per_token FROM market_quote_versions "
                        + "WHERE quote_id = ? AND version = ?",
                BigDecimal.class, initial.quoteId(), initial.version()))
                .isEqualByComparingTo("1.00");
    }

    @Test
    void publicacionesConcurrentesSeSerializanPorJugador() throws Exception {
        fixture();
        var initial = quotes.publishInitialQuote(playerId, new BigDecimal("1.00"), true);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> publishConcurrently(start, "1.10"));
            var second = pool.submit(() -> publishConcurrently(start, "1.20"));
            start.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM market_quote_versions WHERE player_id = ?",
                Long.class, playerId)).isEqualTo(3L);
        assertThat(jdbc.queryForObject(
                "SELECT quote_id FROM market_quote_headers WHERE player_id = ?",
                UUID.class, playerId)).isEqualTo(initial.quoteId());
        assertThat(jdbc.queryForObject(
                "SELECT current_version FROM market_quote_headers WHERE player_id = ?",
                Long.class, playerId)).isEqualTo(3L);
    }

    private void publishConcurrently(CountDownLatch start, String price) {
        try {
            start.await(10, TimeUnit.SECONDS);
            quotes.publish(new QuotePublicationRequest(
                    playerId, new BigDecimal(price), NOW.plusSeconds(60),
                    NOW.plusSeconds(60), null, "concurrent"));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private void fixture() {
        playerId = UUID.randomUUID();
        UUID teamId = jdbc.queryForObject("SELECT id FROM equipos ORDER BY id LIMIT 1", UUID.class);
        jdbc.update("INSERT INTO jugadores "
                + "(id, name, position, jersey_number, nationality, team_id, is_active, "
                + "created_at, updated_at) VALUES (?, 'T040 Quote Player', 'Forward', 10, "
                + "'Argentina', ?, TRUE, ?, ?)", playerId, teamId, DB_NOW, DB_NOW);
    }
}
