package com.devchesterunited.devchesterunitedv2.integration.persistence;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketQuoteAvailability;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketAvailabilityServiceImpl;
import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MarketAvailabilityRepositoryIntegrationTest extends PostgresTestSupport {
    private static final OffsetDateTime DB_NOW =
            OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(10);

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MarketAvailabilityServiceImpl availability;

    private UUID teamId;
    private UUID validPlayer;
    private UUID historicalPlayer;
    private UUID absentPlayer;
    private UUID invalidatedPlayer;
    private String previousStatus;
    private Long previousEpoch;

    @BeforeEach
    void fixture() {
        jdbc.update("INSERT INTO market_state (id, status, epoch, created_at, updated_at) "
                + "VALUES (1, 'UNINITIALIZED', 1, ?, ?) ON CONFLICT (id) DO NOTHING",
                DB_NOW, DB_NOW);
        previousStatus = jdbc.queryForObject("SELECT status FROM market_state WHERE id = 1", String.class);
        previousEpoch = jdbc.queryForObject("SELECT epoch FROM market_state WHERE id = 1", Long.class);
        jdbc.update("UPDATE market_state SET status = 'READY', epoch = epoch + 1 WHERE id = 1");

        teamId = UUID.randomUUID();
        validPlayer = UUID.randomUUID();
        historicalPlayer = UUID.randomUUID();
        absentPlayer = UUID.randomUUID();
        invalidatedPlayer = UUID.randomUUID();
        jdbc.update("INSERT INTO equipos (id, name, league, is_active, created_at, updated_at) "
                + "VALUES (?, 'Availability Team', 'Availability League', TRUE, ?, ?)", teamId, DB_NOW, DB_NOW);
        insertPlayer(validPlayer, "Valid Player");
        insertPlayer(historicalPlayer, "Historical Player");
        insertPlayer(absentPlayer, "Absent Quote Player");
        insertPlayer(invalidatedPlayer, "Invalidated Quote Player");
        UUID validQuote = UUID.randomUUID();
        jdbc.update("INSERT INTO market_quote_versions "
                + "(quote_id, version, player_id, price_per_token, published_at, valid_from, valid_until, validity_status, created_at) "
                + "VALUES (?, 1, ?, 1.00, ?, ?, NULL, 'VALID', ?)",
                validQuote, validPlayer, DB_NOW.minusSeconds(60), DB_NOW.minusSeconds(60), DB_NOW);
        jdbc.update("INSERT INTO market_quote_headers (player_id, quote_id, current_version, updated_at) "
                + "VALUES (?, ?, 1, ?)", validPlayer, validQuote, DB_NOW);
        UUID expiredQuote = UUID.randomUUID();
        jdbc.update("INSERT INTO market_quote_versions "
                + "(quote_id, version, player_id, price_per_token, published_at, valid_from, valid_until, validity_status, created_at) "
                + "VALUES (?, 1, ?, 0.90, ?, ?, ?, 'EXPIRED', ?)",
                expiredQuote, historicalPlayer, DB_NOW.minusSeconds(3600),
                DB_NOW.minusSeconds(3600), DB_NOW.minusSeconds(60), DB_NOW);
        jdbc.update("INSERT INTO market_quote_headers (player_id, quote_id, current_version, updated_at) "
                + "VALUES (?, ?, 1, ?)", historicalPlayer, expiredQuote, DB_NOW);
        UUID invalidatedQuote = UUID.randomUUID();
        jdbc.update("INSERT INTO market_quote_versions "
                + "(quote_id, version, player_id, price_per_token, published_at, valid_from, valid_until, validity_status, created_at) "
                + "VALUES (?, 1, ?, 1.10, ?, ?, NULL, 'VALID', ?)",
                invalidatedQuote, invalidatedPlayer, DB_NOW.minusSeconds(60),
                DB_NOW.minusSeconds(60), DB_NOW);
        jdbc.update("INSERT INTO market_quote_headers "
                + "(player_id, quote_id, current_version, updated_at, validity_status, invalidated_at, invalidation_reason) "
                + "VALUES (?, ?, 1, ?, 'INVALIDATED', ?, 'stale-feed')",
                invalidatedPlayer, invalidatedQuote, DB_NOW, DB_NOW.minusSeconds(1));
    }

    @AfterEach
    void restore() {
        jdbc.update("UPDATE market_state SET status = ?, epoch = ? WHERE id = 1",
                previousStatus, previousEpoch);
    }

    @Test
    void consultaAgregadaNoFallaPorTransaccionReadOnlyConBloqueoDeEstado() {
        var result = availability.consultarJugadores(1, 20);

        assertThat(result.estado().name()).isEqualTo("READY");
        assertThat(result.jugadores()).isNotEmpty();
    }

    @Test
    void consultaAgregadaNoConfundeVersionHistoricaConVigente() {
        var result = availability.consultarJugadores(1, 100);

        assertThat(result.operativa()).isTrue();
        assertThat(result.jugadores()).filteredOn(player -> player.playerId().equals(historicalPlayer))
                .singleElement().satisfies(player -> {
                    assertThat(player.quoteAvailability()).isEqualTo(MarketQuoteAvailability.HISTORICAL);
                    assertThat(player.operable()).isFalse();
                });
    }

    @Test
    void consultaAgregadaNoPresentaUnaCotizacionInvalidadaComoComprable() {
        var result = availability.consultarJugadores(1, 100);

        assertThat(result.jugadores()).filteredOn(player -> player.playerId().equals(invalidatedPlayer))
                .singleElement().satisfies(player -> {
                    assertThat(player.quoteAvailability()).isEqualTo(MarketQuoteAvailability.HISTORICAL);
                    assertThat(player.operable()).isFalse();
                    assertThat(player.validityStatus()).isEqualTo("INVALIDATED");
                });
    }

    @Test
    void consultaAgregadaRespetaPaginacionDeterminista() {
        var primera = availability.consultarJugadores(1, 2);
        var segunda = availability.consultarJugadores(2, 2);

        assertThat(primera.jugadores()).hasSizeLessThanOrEqualTo(2);
        assertThat(segunda.jugadores()).hasSizeLessThanOrEqualTo(2);
        assertThat(primera.pagina()).isEqualTo(1);
        assertThat(segunda.pagina()).isEqualTo(2);
        assertThat(primera.totalRegistros()).isGreaterThanOrEqualTo(3L);
        assertThat(primera.totalPaginas()).isEqualTo((primera.totalRegistros() + 1) / 2);
        assertThat(segunda.totalPaginas()).isEqualTo(primera.totalPaginas());
    }

    private void insertPlayer(UUID playerId, String name) {
        jdbc.update("INSERT INTO jugadores "
                + "(id, name, position, jersey_number, nationality, team_id, is_active, created_at, updated_at) "
                + "VALUES (?, '" + name + "', 'Forward', 10, 'Argentina', ?, TRUE, ?, ?)",
                playerId, teamId, DB_NOW, DB_NOW);
    }
}
