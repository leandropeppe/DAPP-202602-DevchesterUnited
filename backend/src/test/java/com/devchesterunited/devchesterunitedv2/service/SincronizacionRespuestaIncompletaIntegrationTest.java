package com.devchesterunited.devchesterunitedv2.service;

import com.devchesterunited.devchesterunitedv2.adapter.FootballDataException;
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
import com.devchesterunited.devchesterunitedv2.persistence.dto.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.service.interfaces.SincronizacionCatalogoService;
import java.time.Instant;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
class SincronizacionRespuestaIncompletaIntegrationTest extends PostgresTestSupport {
    private static final Instant AHORA = Instant.parse("2026-09-19T12:00:00Z");

    @MockitoBean FootballDataAdapter adapter;
    @Autowired JdbcTemplate jdbc;
    @Autowired SincronizacionCatalogoService servicio;
    @Autowired EquipoRepository equipos;
    @Autowired IdentidadExternaEquipoRepository identidadesEquipos;
    @Autowired JugadorRepository jugadores;

    @BeforeEach
    void limpiarFixturesExternos() {
        jdbc.update("DELETE FROM jugadores WHERE team_id IN (SELECT equipo_id FROM identidades_externas_equipo WHERE proveedor = 'FOOTBALL_DATA')");
        jdbc.update("DELETE FROM equipos WHERE id IN (SELECT equipo_id FROM identidades_externas_equipo WHERE proveedor = 'FOOTBALL_DATA')");
        jdbc.update("UPDATE equipos SET is_active = true");
        jdbc.update("UPDATE jugadores SET is_active = true");
    }

    @Test
    void falloPorRespuestaIncompletaConservaElSnapshotAnteriorSinCambiosParciales() {
        CatalogoSincronizado snapshotValido = snapshot();
        when(adapter.sincronizar()).thenReturn(snapshotValido)
                .thenThrow(new FootballDataException("La respuesta del proveedor no forma un snapshot utilizable."));

        servicio.sincronizar();

        long equiposAntes = jdbc.queryForObject(
                "SELECT count(*) FROM identidades_externas_equipo WHERE proveedor = 'FOOTBALL_DATA'", Long.class);
        long jugadoresAntes = jdbc.queryForObject(
                "SELECT count(*) FROM jugadores j JOIN identidades_externas_jugador i ON i.jugador_id = j.id WHERE i.proveedor = 'FOOTBALL_DATA'", Long.class);
        long equiposActivosAntes = jdbc.queryForObject(
                "SELECT count(*) FROM identidades_externas_equipo i JOIN equipos e ON e.id = i.equipo_id "
                        + "WHERE i.proveedor = 'FOOTBALL_DATA' AND e.is_active", Long.class);
        long jugadoresActivosAntes = jdbc.queryForObject(
                "SELECT count(*) FROM jugadores j JOIN identidades_externas_jugador i ON i.jugador_id = j.id WHERE i.proveedor = 'FOOTBALL_DATA' AND j.is_active", Long.class);
        UUID equipoIdAntes = equipoPorExternalId(600000L).getId();
        UUID jugadorIdAntes = jugadorIdPorExternalId(700000L);

        assertThatThrownBy(servicio::sincronizar)
                .isInstanceOf(FootballDataException.class)
                .hasMessage("La respuesta del proveedor no forma un snapshot utilizable.");

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM identidades_externas_equipo WHERE proveedor = 'FOOTBALL_DATA'", Long.class))
                .isEqualTo(equiposAntes);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM jugadores j JOIN identidades_externas_jugador i ON i.jugador_id = j.id WHERE i.proveedor = 'FOOTBALL_DATA'", Long.class))
                .isEqualTo(jugadoresAntes);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM identidades_externas_equipo i JOIN equipos e ON e.id = i.equipo_id "
                        + "WHERE i.proveedor = 'FOOTBALL_DATA' AND e.is_active", Long.class))
                .isEqualTo(equiposActivosAntes);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM jugadores j JOIN identidades_externas_jugador i ON i.jugador_id = j.id WHERE i.proveedor = 'FOOTBALL_DATA' AND j.is_active", Long.class))
                .isEqualTo(jugadoresActivosAntes);
        assertThat(equipoPorExternalId(600000L))
                .satisfies(equipo -> {
                    assertThat(equipo.getId()).isEqualTo(equipoIdAntes);
                    assertThat(equipo.isActivo()).isTrue();
                });
        assertThat(jugadores.findById(jugadorIdAntes)).get()
                .satisfies(jugador -> {
                    assertThat(jugador.getId()).isEqualTo(jugadorIdAntes);
                    assertThat(jugador.isActivo()).isTrue();
                });
        assertThat(jugadores.contar(null, null, null)).isEqualTo(jugadoresActivosAntes);
        assertThat(duplicados("equipos")).isZero();
        assertThat(duplicados("jugadores")).isZero();
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
        UUID equipoId = identidadPorExternalId(externalId).orElseThrow().getEquipo().getId();
        return equipos.findById(equipoId).orElseThrow();
    }

    private java.util.Optional<com.devchesterunited.devchesterunitedv2.persistence.dto.IdentidadExternaEquipoEntity>
            identidadPorExternalId(long externalId) {
        return identidadesEquipos.findByProveedorAndExternalId(
                ProveedorIdentidadExterna.FOOTBALL_DATA, externalId);
    }

    private UUID jugadorIdPorExternalId(long externalId) {
        return jdbc.queryForObject("SELECT jugador_id FROM identidades_externas_jugador WHERE proveedor = 'FOOTBALL_DATA' AND external_id = ?", UUID.class, externalId);
    }

    private static CatalogoSincronizado snapshot() {
        List<String> ligas = List.of("Premier League", "LaLiga", "Bundesliga", "Serie A", "Ligue 1");
        List<Integer> cantidades = List.of(20, 20, 18, 20, 18);
        List<Equipo> equiposSnapshot = new ArrayList<>();
        List<Jugador> jugadoresSnapshot = new ArrayList<>();
        Map<UUID, Long> ids = new HashMap<>();
        List<IdentidadExternaEquipo> identidadesEquipos = new ArrayList<>();
        int indice = 0;
        for (int liga = 0; liga < ligas.size(); liga++) {
            for (int i = 0; i < cantidades.get(liga); i++) {
                UUID equipoId = UUID.randomUUID();
                equiposSnapshot.add(Equipo.importado(equipoId, "Equipo " + indice, ligas.get(liga),
                        new TemporadaEquipo(2026L, null, null), AHORA));
                identidadesEquipos.add(new IdentidadExternaEquipo(
                        equipoId, ProveedorIdentidadExterna.FOOTBALL_DATA, 600000L + indice));
                Jugador jugadorInterno = new Jugador(UUID.randomUUID(), "Jugador " + indice,
                        equipoId, "Forward", null, "Argentina", AHORA);
                jugadoresSnapshot.add(jugadorInterno);
                ids.put(jugadorInterno.getId(), 700000L + indice);
                indice++;
            }
        }
        return new CatalogoSincronizado(equiposSnapshot, jugadoresSnapshot, ids, identidadesEquipos);
    }
}
