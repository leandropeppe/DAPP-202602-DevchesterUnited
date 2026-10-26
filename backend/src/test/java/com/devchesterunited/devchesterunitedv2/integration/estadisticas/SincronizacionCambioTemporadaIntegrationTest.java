package com.devchesterunited.devchesterunitedv2.integration.estadisticas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.devchesterunited.devchesterunitedv2.modelo.equipo.Equipo;
import com.devchesterunited.devchesterunitedv2.modelo.equipo.IdentidadExternaEquipo;
import com.devchesterunited.devchesterunitedv2.modelo.equipo.TemporadaEquipo;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.CatalogoSincronizado;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.Jugador;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.scraping.catalogo.FootballDataAdapter;
import com.devchesterunited.devchesterunitedv2.service.interfaces.estadisticas.SincronizacionCatalogoService;
import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDate;

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

    @MockitoBean FootballDataAdapter adapter;
    @Autowired JdbcTemplate jdbc;
    @Autowired SincronizacionCatalogoService servicio;
    @Autowired EquipoRepository equipos;
    @Autowired IdentidadExternaEquipoRepository identidadesEquipos;
    @Autowired JugadorRepository jugadores;

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
    void cambiaTemporadaPorLigaConservaIdentidadInactivaAusentesEIncorporaNuevos() {
        CatalogoSincronizado inicial = snapshot(TEMPORADA_INICIAL, false);
        CatalogoSincronizado temporadaNueva = snapshot(TEMPORADA_NUEVA, true);
        when(adapter.sincronizar()).thenReturn(inicial, temporadaNueva);

        servicio.sincronizar();

        UUID equipoPersistenteId = equipoPorExternalId(EQUIPO_PERSISTENTE).getId();
        UUID equipoAusenteId = equipoPorExternalId(EQUIPO_AUSENTE).getId();

        servicio.sincronizar();

        var equipoPersistente = equipoPorExternalId(EQUIPO_PERSISTENTE);
        assertThat(equipoPersistente.getId()).isEqualTo(equipoPersistenteId);
        assertThat(equipoPersistente.isActivo()).isTrue();
        assertThat(equipoPersistente.getTemporadaExternaId()).isEqualTo(3101L);
        assertThat(equipoPersistente.getInicioTemporada()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(equipoPersistente.getFinTemporada()).isEqualTo(LocalDate.of(2027, 5, 31));

        var equipoAusente = equipoPorExternalId(EQUIPO_AUSENTE);
        assertThat(equipoAusente.getId()).isEqualTo(equipoAusenteId);
        assertThat(equipoAusente.isActivo()).isFalse();

        var equipoNuevo = equipoPorExternalId(EQUIPO_NUEVO);
        assertThat(equipoNuevo.isActivo()).isTrue();
        assertThat(equipoNuevo.getTemporadaExternaId()).isEqualTo(3101L);
        assertThat(equipoNuevo.getInicioTemporada()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(equipoNuevo.getFinTemporada()).isEqualTo(LocalDate.of(2027, 5, 31));
        assertThat(equipoNuevo.getId()).isNotEqualTo(equipoAusenteId);

        assertThat(equiposFootballDataActivos()).hasSize(96);
        assertThat(duplicados("equipos")).isZero();
        assertThat(duplicados("jugadores")).isZero();
        assertThat(jugadores.findById(jugadorIdPorExternalId(JUGADOR_NUEVO))).get()
                .extracting(JugadorEntity::isActivo).isEqualTo(true);

        for (Map.Entry<String, Temporada> entry : TEMPORADA_NUEVA.entrySet()) {
            var activosDeLiga = equiposFootballDataActivos().stream()
                    .filter(equipo -> entry.getKey().equals(equipo.getLiga()))
                    .toList();
            assertThat(activosDeLiga).isNotEmpty()
                    .allMatch(equipo -> equipo.getTemporadaExternaId().equals(entry.getValue().id()))
                    .allMatch(equipo -> equipo.getInicioTemporada().equals(entry.getValue().inicio()))
                    .allMatch(equipo -> equipo.getFinTemporada().equals(entry.getValue().fin()));
        }
        assertThat(equiposFootballDataActivos().stream()
                .map(EquipoEntity::getTemporadaExternaId)
                .distinct()).hasSize(5);
    }

    private long duplicados(String tabla) {
        String tablaIdentidad = "jugadores".equals(tabla)
                ? "(SELECT external_id FROM identidades_externas_jugador WHERE proveedor = 'FOOTBALL_DATA') identidades"
                : "(SELECT external_id FROM identidades_externas_equipo WHERE proveedor = 'FOOTBALL_DATA') identidades";
        return jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT external_id
                    FROM %s
                    WHERE external_id IS NOT NULL
                    GROUP BY external_id
                    HAVING count(*) > 1
                ) duplicados
                """.formatted(tablaIdentidad), Long.class);
    }

    private EquipoEntity equipoPorExternalId(long externalId) {
        UUID equipoId = identidadesEquipos.findByProveedorAndExternalId(
                ProveedorIdentidadExterna.FOOTBALL_DATA, externalId).orElseThrow().getEquipo().getId();
        return equipos.findById(equipoId).orElseThrow();
    }

    private List<EquipoEntity> equiposFootballDataActivos() {
        return identidadesEquipos.findAllByProveedor(ProveedorIdentidadExterna.FOOTBALL_DATA).stream()
                .map(identidad -> equipos.findById(identidad.getEquipo().getId()).orElseThrow())
                .filter(EquipoEntity::isActivo)
                .toList();
    }

    private UUID jugadorIdPorExternalId(long externalId) {
        return jdbc.queryForObject("SELECT jugador_id FROM identidades_externas_jugador WHERE proveedor = 'FOOTBALL_DATA' AND external_id = ?", UUID.class, externalId);
    }

    private static CatalogoSincronizado snapshot(Map<String, Temporada> temporadas,
            boolean reemplazarEquipoAusente) {
        List<String> ligas = List.of("Premier League", "LaLiga", "Bundesliga", "Serie A", "Ligue 1");
        List<Integer> cantidades = List.of(20, 20, 18, 20, 18);
        List<Equipo> equiposSnapshot = new ArrayList<>();
        List<Jugador> jugadoresSnapshot = new ArrayList<>();
        Map<UUID, Long> ids = new HashMap<>();
        List<IdentidadExternaEquipo> identidadesEquipos = new ArrayList<>();
        int indice = 0;
        for (int liga = 0; liga < ligas.size(); liga++) {
            String nombreLiga = ligas.get(liga);
            Temporada temporada = temporadas.get(nombreLiga);
            for (int i = 0; i < cantidades.get(liga); i++) {
                boolean reemplazo = reemplazarEquipoAusente && indice == 0;
                long idExternoEquipo = reemplazo ? EQUIPO_NUEVO : EQUIPO_AUSENTE + indice;
                UUID equipoId = UUID.randomUUID();
                equiposSnapshot.add(Equipo.importado(equipoId,
                        reemplazo ? "Equipo Nuevo" : "Equipo " + indice, nombreLiga,
                        new TemporadaEquipo(temporada.id(), temporada.inicio(), temporada.fin()), AHORA));
                identidadesEquipos.add(new IdentidadExternaEquipo(
                        equipoId, ProveedorIdentidadExterna.FOOTBALL_DATA, idExternoEquipo));
                long idExternoJugador = reemplazo ? JUGADOR_NUEVO : JUGADOR_BASE + indice;
                Jugador jugadorInterno = new Jugador(UUID.randomUUID(), "Jugador " + idExternoJugador,
                        equipoId, "Forward", null, "Argentina", AHORA);
                jugadoresSnapshot.add(jugadorInterno);
                ids.put(jugadorInterno.getId(), idExternoJugador);
                indice++;
            }
        }
        return new CatalogoSincronizado(equiposSnapshot, jugadoresSnapshot, ids, identidadesEquipos);
    }

    private record Temporada(Long id, LocalDate inicio, LocalDate fin) { }
}
