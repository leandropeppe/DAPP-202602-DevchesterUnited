package com.devchesterunited.devchesterunitedv2.persistence;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.VersionCotizacionMercadoRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteValidityStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuotePublicationRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteInvalidationRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteInvalidationResult;
import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.impl.market.QuotePublicationService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.QuotePublicationPort;
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
    void validUntilEsUnLimiteExclusivoEnPostgresql() {
        fixture();
        var initial = quotes.publishInitialQuote(playerId, new BigDecimal("1.00"), true);
        var expiring = quotes.publish(new QuotePublicationRequest(
                playerId, new BigDecimal("1.25"), NOW.plusSeconds(10),
                NOW.plusSeconds(10), NOW.plusSeconds(60), "valuation-v1"));

        assertThat(versions.findExactVigente(playerId, expiring.quoteId(), expiring.version(),
                expiring.validUntil().minusNanos(1_000))).isPresent();
        assertThat(versions.findExactVigente(playerId, expiring.quoteId(), expiring.version(),
                expiring.validUntil())).isEmpty();
        assertThat(versions.findExactVigente(playerId, initial.quoteId(), initial.version(),
                expiring.validUntil())).isEmpty();
    }

    @Test
    void cotizacionInicialSinVencimientoSigueVigenteEnUnInstanteFuturo() {
        fixture();
        var initial = quotes.publishInitialQuote(playerId, new BigDecimal("1.00"), true);

        assertThat(initial.validUntil()).isNull();
        assertThat(versions.findExactVigente(playerId, initial.quoteId(), initial.version(),
                NOW.plusSeconds(365L * 24 * 60 * 60))).isPresent();
    }

    @Test
    void invalidacionPersisteSoloEnLaCabeceraYLaPublicacionPosteriorLaRestablece() {
        fixture();
        var initial = quotes.publishInitialQuote(playerId, new BigDecimal("1.00"), true);

        QuoteInvalidationResult invalidated = quotes.invalidate(
                new QuoteInvalidationRequest(playerId, initial.quoteId(), initial.version(),
                        "  obsolete-statistics  "));

        assertThat(invalidated.reason()).isEqualTo("obsolete-statistics");
        assertThat(jdbc.queryForObject(
                "SELECT validity_status FROM market_quote_headers WHERE player_id = ?",
                String.class, playerId)).isEqualTo("INVALIDATED");
        assertThat(jdbc.queryForObject(
                "SELECT invalidation_reason FROM market_quote_headers WHERE player_id = ?",
                String.class, playerId)).isEqualTo("obsolete-statistics");
        assertThat(versions.findExactVigente(playerId, initial.quoteId(), initial.version(), NOW))
                .isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT validity_status FROM market_quote_versions WHERE quote_id = ? AND version = ?",
                String.class, initial.quoteId(), initial.version())).isEqualTo("VALID");
        assertThat(quotes.currentQuote(playerId)).get()
                .extracting(value -> value.validityStatus())
                .isEqualTo(com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteValidityStatus.INVALIDATED);

        var replacement = quotes.publish(new QuotePublicationRequest(
                playerId, new BigDecimal("1.25"), NOW, NOW, null, "valuation-v2"));

        assertThat(replacement.version()).isEqualTo(2L);
        assertThat(jdbc.queryForObject(
                "SELECT validity_status FROM market_quote_headers WHERE player_id = ?",
                String.class, playerId)).isEqualTo("VALID");
        assertThat(jdbc.queryForObject(
                "SELECT invalidated_at FROM market_quote_headers WHERE player_id = ?",
                OffsetDateTime.class, playerId)).isNull();
        assertThat(versions.findExactVigente(playerId, replacement.quoteId(), replacement.version(), NOW))
                .isPresent();
    }

    @Test
    void reintentoDeInvalidacionEsIdempotenteYRecuperaLosDatosPersistidos() {
        fixture();
        var initial = quotes.publishInitialQuote(playerId, new BigDecimal("1.00"), true);
        var request = new QuoteInvalidationRequest(
                playerId, initial.quoteId(), initial.version(), "stale-feed");

        var first = quotes.invalidate(request);
        var retry = quotes.invalidate(new QuoteInvalidationRequest(
                playerId, initial.quoteId(), initial.version(), "otro-motivo"));

        assertThat(retry).isEqualTo(first);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM market_quote_versions WHERE player_id = ?",
                Long.class, playerId)).isEqualTo(1L);
    }

    @Test
    void invalidacionDeVersionAntiguaNoAfectaUnaPublicacionPosterior() {
        fixture();
        var initial = quotes.publishInitialQuote(playerId, new BigDecimal("1.00"), true);
        var replacement = quotes.publish(new QuotePublicationRequest(
                playerId, new BigDecimal("1.50"), NOW, NOW, null, "valuation-v2"));

        assertThatThrownBy(() -> quotes.invalidate(new QuoteInvalidationRequest(
                playerId, initial.quoteId(), initial.version(), "obsolete")))
                .isInstanceOf(TokenMarketException.class);
        assertThat(jdbc.queryForObject(
                "SELECT current_version FROM market_quote_headers WHERE player_id = ?",
                Long.class, playerId)).isEqualTo(replacement.version());
        assertThat(jdbc.queryForObject(
                "SELECT validity_status FROM market_quote_headers WHERE player_id = ?",
                String.class, playerId)).isEqualTo("VALID");
    }

    @Test
    void reintentosConcurrentesDeInvalidacionNoDuplicanNiSobrescribenElResultado() throws Exception {
        fixture();
        var initial = quotes.publishInitialQuote(playerId, new BigDecimal("1.00"), true);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> invalidateConcurrently(start, "first"));
            var second = pool.submit(() -> invalidateConcurrently(start, "second"));
            start.countDown();
            QuoteInvalidationResult firstResult = first.get(30, TimeUnit.SECONDS);
            QuoteInvalidationResult secondResult = second.get(30, TimeUnit.SECONDS);

            assertThat(firstResult).isEqualTo(secondResult);
            assertThat(jdbc.queryForObject(
                    "SELECT invalidation_reason FROM market_quote_headers WHERE player_id = ?",
                    String.class, playerId)).isEqualTo(firstResult.reason());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void publicacionEInvalidacionSimultaneasNoCorrompenLaReferenciaVigente() throws Exception {
        fixture();
        var initial = quotes.publishInitialQuote(playerId, new BigDecimal("1.00"), true);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var publication = pool.submit(() -> {
                await(start);
                return quotes.publish(new QuotePublicationRequest(
                        playerId, new BigDecimal("1.40"), NOW, NOW, null, "valuation-v2"));
            });
            var invalidation = pool.submit(() -> {
                await(start);
                return quotes.invalidate(new QuoteInvalidationRequest(
                        playerId, initial.quoteId(), initial.version(), "stale"));
            });
            start.countDown();
            try {
                publication.get(30, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                throw new AssertionError("La publicacion concurrente no debe fallar", ignored);
            }
            try {
                invalidation.get(30, TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException expected) {
                // If publication won, the exact old reference must be rejected.
            }
        } finally {
            pool.shutdownNow();
        }

        Integer statusCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM market_quote_headers WHERE player_id = ? "
                        + "AND validity_status IN ('VALID', 'INVALIDATED')",
                Integer.class, playerId);
        assertThat(statusCount).isEqualTo(1);
    }

    @Test
    void publicadorExternoConservaPrecioEstrategiaVigenciaYVersionaLaCabecera() {
        fixture();
        QuotePublicationPort externalValuationPublisher = quotes;
        BigDecimal calculatedPrice = new BigDecimal("2.35");
        Instant publishedAt = NOW.plusSeconds(60);
        Instant validUntil = NOW.plusSeconds(3600);

        var published = externalValuationPublisher.publish(new QuotePublicationRequest(
                playerId, calculatedPrice, publishedAt, publishedAt, validUntil,
                "valuation-strategy-v3"));

        assertThat(published.pricePerToken()).isEqualByComparingTo(calculatedPrice);
        assertThat(published.strategyReference()).isEqualTo("valuation-strategy-v3");
        assertThat(published.publishedAt()).isEqualTo(publishedAt);
        assertThat(published.validFrom()).isEqualTo(publishedAt);
        assertThat(published.validUntil()).isEqualTo(validUntil);
        assertThat(jdbc.queryForObject(
                "SELECT current_version FROM market_quote_headers WHERE player_id = ?",
                Long.class, playerId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT price_per_token FROM market_quote_versions "
                        + "WHERE player_id = ? AND quote_id = ? AND version = ?",
                BigDecimal.class, playerId, published.quoteId(), published.version()))
                .isEqualByComparingTo(calculatedPrice);
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

    private QuoteInvalidationResult invalidateConcurrently(CountDownLatch start, String reason) {
        await(start);
        var current = quotes.currentQuote(playerId).orElseThrow();
        return quotes.invalidate(new QuoteInvalidationRequest(
                playerId, current.quoteId(), current.version(), reason));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("La coordinacion concurrente expiro.");
            }
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
