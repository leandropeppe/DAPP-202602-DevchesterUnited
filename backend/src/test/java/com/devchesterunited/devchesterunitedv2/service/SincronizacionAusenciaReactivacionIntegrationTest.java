package com.devchesterunited.devchesterunitedv2.service;

import com.devchesterunited.devchesterunitedv2.adapter.impl.FootballDataAdapter;
import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.modelo.CatalogoSincronizado;
import com.devchesterunited.devchesterunitedv2.modelo.Equipo;
import com.devchesterunited.devchesterunitedv2.modelo.Jugador;
import com.devchesterunited.devchesterunitedv2.persistence.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.JugadorRepository;
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
    @Autowired JugadorRepository jugadores;

    @BeforeEach
    void limpiarFixturesExternos() {
        jdbc.update("DELETE FROM jugadores WHERE team_id IN (SELECT id FROM equipos WHERE external_id IS NOT NULL)");
        jdbc.update("DELETE FROM equipos WHERE external_id IS NOT NULL");
        jdbc.update("UPDATE equipos SET is_active = true");
        jdbc.update("UPDATE jugadores SET is_active = true");
    }

    @Test
    void entidadesAusentesSeInactivanYReaparecenConLaMismaIdentidad() {
        CatalogoSincronizado inicial = snapshot(false);
        CatalogoSincronizado sinEntidadOriginal = snapshot(true);
        CatalogoSincronizado entidadOriginalReaparece = snapshot(false);
        when(adapter.sincronizar()).thenReturn(inicial, sinEntidadOriginal, entidadOriginalReaparece);

        servicio.sincronizar();

        var equipoInicial = equipos.findByIdExterno(EQUIPO_AUSENTE).orElseThrow();
        UUID equipoIdInicial = equipoInicial.getId();
        List<UUID> jugadoresIdsIniciales = jugadores.findAllByEquipoId(equipoIdInicial).stream()
                .map(jugador -> jugador.getId()).sorted().toList();
        assertThat(jugadoresIdsIniciales).hasSize(2);
        assertThat(jugadores.contar(null, "Equipo Original 0", null)).isEqualTo(2);

        servicio.sincronizar();

        var equipoAusente = equipos.findByIdExterno(EQUIPO_AUSENTE).orElseThrow();
        assertThat(equipoAusente.isActivo()).isFalse();
        assertThat(equipoAusente.getId()).isEqualTo(equipoIdInicial);
        assertThat(jugadores.findAllByEquipoId(equipoIdInicial))
                .hasSize(2).allMatch(jugador -> !jugador.isActivo());
        assertThat(jugadores.contar(null, "Equipo Original 0", null)).isZero();
        assertThat(jugadores.buscar(null, "Equipo Original 0", null, 0, 20)).isEmpty();
        assertThat(jugadores.contar(null, "Equipo Reemplazo", null)).isEqualTo(2);
        assertThat(equipos.findByIdExterno(EQUIPO_REEMPLAZO)).get()
                .extracting(equipo -> equipo.isActivo()).isEqualTo(true);
        assertThat(activosExternos()).isEqualTo(96L);

        servicio.sincronizar();

        var equipoReactivado = equipos.findByIdExterno(EQUIPO_AUSENTE).orElseThrow();
        assertThat(equipoReactivado.isActivo()).isTrue();
        assertThat(equipoReactivado.getId()).isEqualTo(equipoIdInicial);
        assertThat(equipoReactivado.getIdExterno()).isEqualTo(EQUIPO_AUSENTE);
        assertThat(jugadores.findAllByEquipoId(equipoIdInicial)).hasSize(2)
                .allMatch(jugador -> jugador.isActivo())
                .extracting(jugador -> jugador.getId())
                .containsExactlyInAnyOrderElementsOf(jugadoresIdsIniciales);
        assertThat(jugadores.findAllById(List.of(jugadorIdPorExternalId(JUGADOR_AUSENTE_BASE), jugadorIdPorExternalId(JUGADOR_AUSENTE_BASE + 1))))
                .hasSize(2).allMatch(jugador -> jugador.isActivo());
        assertThat(jugadores.contar(null, "Equipo Original 0", null)).isEqualTo(2);
        assertThat(equipos.findByIdExterno(EQUIPO_REEMPLAZO)).get()
                .extracting(equipo -> equipo.isActivo()).isEqualTo(false);
        assertThat(activosExternos()).isEqualTo(96L);
        assertThat(duplicadosExternalId()).isZero();
    }

    private long activosExternos() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM equipos WHERE external_id IS NOT NULL AND is_active", Long.class);
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

    private UUID jugadorIdPorExternalId(long externalId) {
        return jdbc.queryForObject("SELECT jugador_id FROM identidades_externas_jugador WHERE proveedor = 'FOOTBALL_DATA' AND external_id = ?", UUID.class, externalId);
    }

    private static CatalogoSincronizado snapshot(boolean reemplazarEquipoOriginal) {
        List<String> ligas = List.of("Premier League", "LaLiga", "Bundesliga", "Serie A", "Ligue 1");
        List<Integer> cantidades = List.of(20, 20, 18, 20, 18);
        List<Equipo> equiposSnapshot = new ArrayList<>();
        List<Jugador> jugadoresSnapshot = new ArrayList<>();
        Map<UUID, Long> ids = new HashMap<>();
        int indice = 0;
        for (int liga = 0; liga < ligas.size(); liga++) {
            for (int i = 0; i < cantidades.get(liga); i++) {
                boolean reemplazo = reemplazarEquipoOriginal && indice == 0;
                long idExternoEquipo = reemplazo ? EQUIPO_REEMPLAZO : EQUIPO_AUSENTE + indice;
                String nombreEquipo = reemplazo ? "Equipo Reemplazo" : "Equipo Original " + indice;
                UUID equipoId = UUID.randomUUID();
                equiposSnapshot.add(Equipo.importado(equipoId, idExternoEquipo, nombreEquipo, "E", "E",
                        ligas.get(liga), null, 2026L, LocalDate.of(2026, 8, 1),
                        LocalDate.of(2027, 5, 31), AHORA));
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
        return new CatalogoSincronizado(equiposSnapshot, jugadoresSnapshot, ids);
    }
}
