package com.devchesterunited.devchesterunitedv2.service;

import com.devchesterunited.devchesterunitedv2.adapter.impl.FootballDataAdapter;
import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.modelo.CatalogoSincronizado;
import com.devchesterunited.devchesterunitedv2.modelo.Equipo;
import com.devchesterunited.devchesterunitedv2.modelo.IdentidadExternaEquipo;
import com.devchesterunited.devchesterunitedv2.modelo.Jugador;
import com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.modelo.TemporadaEquipo;
import com.devchesterunited.devchesterunitedv2.persistence.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.service.impl.MarketInitializationBootstrap;
import com.devchesterunited.devchesterunitedv2.service.interfaces.SincronizacionCatalogoService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EquipoEntity;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
class SincronizacionAusenciaReactivacionIntegrationTest extends PostgresTestSupport {
    private static final Instant AHORA = Instant.parse("2026-09-18T12:00:00Z");
    private static final long EQUIPO_AUSENTE = 600000L;
    private static final long EQUIPO_REEMPLAZO = 700000L;
    private static final long JUGADOR_AUSENTE_BASE = 800000L;
    private static final long JUGADOR_REEMPLAZO_BASE = 900000L;

    @MockitoBean FootballDataAdapter adapter;
    @Autowired JdbcTemplate jdbc;
    @Autowired SincronizacionCatalogoService servicio;
    @Autowired EquipoRepository equipos;
    @Autowired IdentidadExternaEquipoRepository identidadesEquipos;
    @Autowired JugadorRepository jugadores;
    @Autowired MarketInitializationBootstrap bootstrap;

    @BeforeEach
    void limpiarFixturesExternos() {
        jdbc.update("DELETE FROM market_token_issuances WHERE player_id IN "
                + "(SELECT jugador_id FROM identidades_externas_jugador WHERE proveedor = 'FOOTBALL_DATA')");
        jdbc.update("DELETE FROM jugadores WHERE team_id IN (SELECT equipo_id FROM identidades_externas_equipo WHERE proveedor = 'FOOTBALL_DATA')");
        jdbc.update("DELETE FROM equipos WHERE id IN (SELECT equipo_id FROM identidades_externas_equipo WHERE proveedor = 'FOOTBALL_DATA')");
        jdbc.update("UPDATE equipos SET is_active = true");
        jdbc.update("UPDATE jugadores SET is_active = true");
    }

    @Test
    void entidadesAusentesSeInactivanYReaparecenConLaMismaIdentidad() {
        CatalogoSincronizado inicial = snapshot(false);
        CatalogoSincronizado sinEntidadOriginal = snapshot(true);
        CatalogoSincronizado entidadOriginalReaparece = snapshot(false);
        when(adapter.sincronizar()).thenReturn(inicial, sinEntidadOriginal, entidadOriginalReaparece);

        // Deja creada la SUPERUSER antes de que el primer snapshot publique
        // activaciones. El listener se ejecuta AFTER_COMMIT.
        bootstrap.bootstrap();
        servicio.sincronizar();

        var equipoInicial = equipoPorExternalId(EQUIPO_AUSENTE);
        UUID equipoIdInicial = equipoInicial.getId();
        List<UUID> jugadoresIdsIniciales = jugadores.findAllByEquipoId(equipoIdInicial).stream()
                .map(JugadorEntity::getId).sorted().toList();
        assertThat(jugadoresIdsIniciales).hasSize(2);
        assertThat(jugadores.contar(null, "Equipo Original 0", null)).isEqualTo(2);
        assertThat(emisionesDeJugadoresOriginales()).isEqualTo(2L);

        servicio.sincronizar();

        var equipoAusente = equipoPorExternalId(EQUIPO_AUSENTE);
        assertThat(equipoAusente.isActivo()).isFalse();
        assertThat(equipoAusente.getId()).isEqualTo(equipoIdInicial);
        assertThat(jugadores.findAllByEquipoId(equipoIdInicial))
                .hasSize(2).allMatch(jugador -> !jugador.isActivo());
        assertThat(jugadores.contar(null, "Equipo Original 0", null)).isZero();
        assertThat(jugadores.buscar(null, "Equipo Original 0", null, 0, 20)).isEmpty();
        assertThat(jugadores.contar(null, "Equipo Reemplazo", null)).isEqualTo(2);
        assertThat(equipoPorExternalId(EQUIPO_REEMPLAZO).isActivo()).isTrue();
        assertThat(activosExternos()).isEqualTo(96L);

        servicio.sincronizar();

        var equipoReactivado = equipoPorExternalId(EQUIPO_AUSENTE);
        assertThat(equipoReactivado.isActivo()).isTrue();
        assertThat(equipoReactivado.getId()).isEqualTo(equipoIdInicial);
        assertThat(identidadPorExternalId(EQUIPO_AUSENTE).orElseThrow().getExternalId())
                .isEqualTo(EQUIPO_AUSENTE);
        assertThat(jugadores.findAllByEquipoId(equipoIdInicial)).hasSize(2)
                .allMatch(JugadorEntity::isActivo)
                .extracting(JugadorEntity::getId)
                .containsExactlyInAnyOrderElementsOf(jugadoresIdsIniciales);
        assertThat(jugadores.findAllById(List.of(
                jugadorIdPorExternalId(JUGADOR_AUSENTE_BASE),
                jugadorIdPorExternalId(JUGADOR_AUSENTE_BASE + 1))))
                .hasSize(2).allMatch(JugadorEntity::isActivo);
        assertThat(jugadores.contar(null, "Equipo Original 0", null)).isEqualTo(2);
        assertThat(equipoPorExternalId(EQUIPO_REEMPLAZO).isActivo()).isFalse();
        assertThat(activosExternos()).isEqualTo(96L);
        assertThat(duplicadosExternalId()).isZero();
        assertThat(emisionesDeJugadoresOriginales()).isEqualTo(2L);
    }

    private long activosExternos() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM equipos e JOIN identidades_externas_equipo i ON i.equipo_id = e.id "
                        + "WHERE i.proveedor = 'FOOTBALL_DATA' AND e.is_active", Long.class);
    }

    private EquipoEntity equipoPorExternalId(long externalId) {
        UUID equipoId = identidadPorExternalId(externalId).orElseThrow().getEquipo().getId();
        return equipos.findById(equipoId).orElseThrow();
    }

    private java.util.Optional<com.devchesterunited.devchesterunitedv2.persistence.dto.IdentidadExternaEquipoEntity>
            identidadPorExternalId(long externalId) {
        return identidadesEquipos.findByProveedorAndExternalId(
                ProveedorIdentidadExterna.FOOTBALL_DATA, externalId);
    }

    private long duplicadosExternalId() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT external_id
                    FROM identidades_externas_jugador
                    WHERE proveedor = 'FOOTBALL_DATA'
                    GROUP BY external_id
                    HAVING count(*) > 1
                ) duplicados
                """, Long.class);
    }

    private long emisionesDeJugadoresOriginales() {
        return jdbc.queryForObject("SELECT count(*) FROM market_token_issuances "
                + "WHERE player_id IN (SELECT jugador_id FROM identidades_externas_jugador "
                + "WHERE proveedor = 'FOOTBALL_DATA' AND external_id IN (?, ?))",
                Long.class, JUGADOR_AUSENTE_BASE, JUGADOR_AUSENTE_BASE + 1);
    }

    private UUID jugadorIdPorExternalId(long externalId) {
        return jdbc.queryForObject("SELECT jugador_id FROM identidades_externas_jugador WHERE proveedor = 'FOOTBALL_DATA' AND external_id = ?", UUID.class, externalId);
    }

    private static CatalogoSincronizado snapshot(boolean reemplazarEquipoOriginal) {
        List<String> ligas = List.of("Premier League", "LaLiga", "Bundesliga", "Serie A", "Ligue 1");
        List<Integer> cantidades = List.of(20, 20, 18, 20, 18);
        List<Equipo> equiposSnapshot = new ArrayList<>();
        List<Jugador> jugadoresSnapshot = new ArrayList<>();
        Map<UUID, Long> ids = new HashMap<>();
        List<IdentidadExternaEquipo> identidadesEquipos = new ArrayList<>();
        int indice = 0;
        for (int liga = 0; liga < ligas.size(); liga++) {
            for (int i = 0; i < cantidades.get(liga); i++) {
                boolean reemplazo = reemplazarEquipoOriginal && indice == 0;
                long idExternoEquipo = reemplazo ? EQUIPO_REEMPLAZO : EQUIPO_AUSENTE + indice;
                String nombreEquipo = reemplazo ? "Equipo Reemplazo" : "Equipo Original " + indice;
                UUID equipoId = UUID.randomUUID();
                equiposSnapshot.add(Equipo.importado(equipoId, nombreEquipo, ligas.get(liga),
                        new TemporadaEquipo(2026L, LocalDate.of(2026, 8, 1),
                                LocalDate.of(2027, 5, 31)), AHORA));
                identidadesEquipos.add(new IdentidadExternaEquipo(
                        equipoId, ProveedorIdentidadExterna.FOOTBALL_DATA, idExternoEquipo));
                for (int jugador = 0; jugador < 2; jugador++) {
                    long idExternoJugador = reemplazo
                            ? JUGADOR_REEMPLAZO_BASE + jugador
                            : JUGADOR_AUSENTE_BASE + indice * 2L + jugador;
                    Jugador jugadorInterno = new Jugador(UUID.randomUUID(), "Jugador " + idExternoJugador,
                            equipoId, "Forward", jugador == 0 ? null : 9, "Argentina", AHORA);
                    jugadoresSnapshot.add(jugadorInterno);
                    ids.put(jugadorInterno.getId(), idExternoJugador);
                }
                indice++;
            }
        }
        return new CatalogoSincronizado(equiposSnapshot, jugadoresSnapshot, ids, identidadesEquipos);
    }
}
