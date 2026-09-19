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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
class SincronizacionCambioTemporadaIntegrationTest extends PostgresTestSupport {
    private static final Instant AHORA = Instant.parse("2026-09-19T12:00:00Z");
    private static final long EQUIPO_AUSENTE = 600000L;
    private static final long EQUIPO_PERSISTENTE = 600001L;
    private static final long EQUIPO_NUEVO = 700000L;
    private static final long JUGADOR_BASE = 800000L;
    private static final long JUGADOR_NUEVO = 900000L;

    private static final Map<String, Temporada> TEMPORADA_INICIAL = Map.of(
            "Premier League", new Temporada(2101L, LocalDate.of(2025, 8, 1), LocalDate.of(2026, 5, 31)),
            "LaLiga", new Temporada(2102L, LocalDate.of(2025, 8, 10), LocalDate.of(2026, 5, 24)),
            "Bundesliga", new Temporada(2103L, LocalDate.of(2025, 8, 15), LocalDate.of(2026, 5, 16)),
            "Serie A", new Temporada(2104L, LocalDate.of(2025, 8, 20), LocalDate.of(2026, 5, 30)),
            "Ligue 1", new Temporada(2105L, LocalDate.of(2025, 8, 22), LocalDate.of(2026, 5, 17)));

    private static final Map<String, Temporada> TEMPORADA_NUEVA = Map.of(
            "Premier League", new Temporada(3101L, LocalDate.of(2026, 8, 1), LocalDate.of(2027, 5, 31)),
            "LaLiga", new Temporada(3102L, LocalDate.of(2026, 8, 10), LocalDate.of(2027, 5, 24)),
            "Bundesliga", new Temporada(3103L, LocalDate.of(2026, 8, 15), LocalDate.of(2027, 5, 16)),
            "Serie A", new Temporada(3104L, LocalDate.of(2026, 8, 20), LocalDate.of(2027, 5, 30)),
            "Ligue 1", new Temporada(3105L, LocalDate.of(2026, 8, 22), LocalDate.of(2027, 5, 17)));

    @MockBean FootballDataAdapter adapter;
    @Autowired JdbcTemplate jdbc;
    @Autowired SincronizacionCatalogoService servicio;
    @Autowired EquipoRepository equipos;
    @Autowired JugadorRepository jugadores;

    @BeforeEach
    void limpiarFixturesExternos() {
        jdbc.update("DELETE FROM jugadores WHERE external_id IS NOT NULL");
        jdbc.update("DELETE FROM equipos WHERE external_id IS NOT NULL");
        jdbc.update("UPDATE equipos SET is_active = true");
        jdbc.update("UPDATE jugadores SET is_active = true");
    }

    @Test
    void cambiaTemporadaPorLigaConservaIdentidadInactivaAusentesEIncorporaNuevos() {
        CatalogoSincronizado inicial = snapshot(TEMPORADA_INICIAL, false);
        CatalogoSincronizado temporadaNueva = snapshot(TEMPORADA_NUEVA, true);
        when(adapter.sincronizar()).thenReturn(inicial, temporadaNueva);

        servicio.sincronizar();

        UUID equipoPersistenteId = equipos.findByIdExterno(EQUIPO_PERSISTENTE).orElseThrow().getId();
        UUID equipoAusenteId = equipos.findByIdExterno(EQUIPO_AUSENTE).orElseThrow().getId();

        servicio.sincronizar();

        var equipoPersistente = equipos.findByIdExterno(EQUIPO_PERSISTENTE).orElseThrow();
        assertThat(equipoPersistente.getId()).isEqualTo(equipoPersistenteId);
        assertThat(equipoPersistente.isActivo()).isTrue();
        assertThat(equipoPersistente.getTemporadaExternaId()).isEqualTo(3101L);
        assertThat(equipoPersistente.getInicioTemporada()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(equipoPersistente.getFinTemporada()).isEqualTo(LocalDate.of(2027, 5, 31));

        var equipoAusente = equipos.findByIdExterno(EQUIPO_AUSENTE).orElseThrow();
        assertThat(equipoAusente.getId()).isEqualTo(equipoAusenteId);
        assertThat(equipoAusente.isActivo()).isFalse();

        var equipoNuevo = equipos.findByIdExterno(EQUIPO_NUEVO).orElseThrow();
        assertThat(equipoNuevo.isActivo()).isTrue();
        assertThat(equipoNuevo.getTemporadaExternaId()).isEqualTo(3101L);
        assertThat(equipoNuevo.getInicioTemporada()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(equipoNuevo.getFinTemporada()).isEqualTo(LocalDate.of(2027, 5, 31));
        assertThat(equipoNuevo.getId()).isNotEqualTo(equipoAusenteId);

        assertThat(equipos.findAllByActivoTrue()).filteredOn(equipo -> equipo.getIdExterno() != null)
                .hasSize(96);
        assertThat(duplicados("equipos")).isZero();
        assertThat(duplicados("jugadores")).isZero();
        assertThat(jugadores.findByIdExterno(JUGADOR_NUEVO)).get()
                .extracting(jugador -> jugador.isActivo()).isEqualTo(true);

        for (Map.Entry<String, Temporada> entry : TEMPORADA_NUEVA.entrySet()) {
            var activosDeLiga = equipos.findAllByActivoTrue().stream()
                    .filter(equipo -> entry.getKey().equals(equipo.getLiga()))
                    .filter(equipo -> equipo.getIdExterno() != null)
                    .toList();
            assertThat(activosDeLiga).isNotEmpty()
                    .allMatch(equipo -> equipo.getTemporadaExternaId().equals(entry.getValue().id()))
                    .allMatch(equipo -> equipo.getInicioTemporada().equals(entry.getValue().inicio()))
                    .allMatch(equipo -> equipo.getFinTemporada().equals(entry.getValue().fin()));
        }
        assertThat(equipos.findAllByActivoTrue().stream()
                .filter(equipo -> equipo.getIdExterno() != null)
                .map(equipo -> equipo.getTemporadaExternaId())
                .distinct()).hasSize(5);
    }

    private long duplicados(String tabla) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT external_id
                    FROM %s
                    WHERE external_id IS NOT NULL
                    GROUP BY external_id
                    HAVING count(*) > 1
                ) duplicados
                """.formatted(tabla), Long.class);
    }

    private static CatalogoSincronizado snapshot(Map<String, Temporada> temporadas,
            boolean reemplazarEquipoAusente) {
        List<String> ligas = List.of("Premier League", "LaLiga", "Bundesliga", "Serie A", "Ligue 1");
        List<Integer> cantidades = List.of(20, 20, 18, 20, 18);
        List<Equipo> equiposSnapshot = new ArrayList<>();
        List<Jugador> jugadoresSnapshot = new ArrayList<>();
        int indice = 0;
        for (int liga = 0; liga < ligas.size(); liga++) {
            String nombreLiga = ligas.get(liga);
            Temporada temporada = temporadas.get(nombreLiga);
            for (int i = 0; i < cantidades.get(liga); i++) {
                boolean reemplazo = reemplazarEquipoAusente && indice == 0;
                long idExternoEquipo = reemplazo ? EQUIPO_NUEVO : EQUIPO_AUSENTE + indice;
                UUID equipoId = UUID.randomUUID();
                equiposSnapshot.add(Equipo.importado(equipoId, idExternoEquipo,
                        reemplazo ? "Equipo Nuevo" : "Equipo " + indice, "E", "E", nombreLiga, null,
                        temporada.id(), temporada.inicio(), temporada.fin(), AHORA));
                long idExternoJugador = reemplazo ? JUGADOR_NUEVO : JUGADOR_BASE + indice;
                jugadoresSnapshot.add(Jugador.importado(UUID.randomUUID(), idExternoJugador,
                        "Jugador " + idExternoJugador, equipoId, "Forward", null, "Argentina", AHORA));
                indice++;
            }
        }
        return new CatalogoSincronizado(equiposSnapshot, jugadoresSnapshot);
    }

    private record Temporada(Long id, LocalDate inicio, LocalDate fin) { }
}
