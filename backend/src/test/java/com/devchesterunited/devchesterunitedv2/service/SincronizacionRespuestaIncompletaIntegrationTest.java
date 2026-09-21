package com.devchesterunited.devchesterunitedv2.service;

import com.devchesterunited.devchesterunitedv2.adapter.FootballDataException;
import com.devchesterunited.devchesterunitedv2.adapter.impl.FootballDataAdapter;
import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.modelo.CatalogoSincronizado;
import com.devchesterunited.devchesterunitedv2.modelo.Equipo;
import com.devchesterunited.devchesterunitedv2.modelo.Jugador;
import com.devchesterunited.devchesterunitedv2.persistence.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.service.interfaces.SincronizacionCatalogoService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
    @Autowired JugadorRepository jugadores;

    @BeforeEach
    void limpiarFixturesExternos() {
        jdbc.update("DELETE FROM jugadores WHERE external_id IS NOT NULL");
        jdbc.update("DELETE FROM equipos WHERE external_id IS NOT NULL");
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
                "SELECT count(*) FROM equipos WHERE external_id IS NOT NULL", Long.class);
        long jugadoresAntes = jdbc.queryForObject(
                "SELECT count(*) FROM jugadores WHERE external_id IS NOT NULL", Long.class);
        long equiposActivosAntes = jdbc.queryForObject(
                "SELECT count(*) FROM equipos WHERE external_id IS NOT NULL AND is_active", Long.class);
        long jugadoresActivosAntes = jdbc.queryForObject(
                "SELECT count(*) FROM jugadores WHERE external_id IS NOT NULL AND is_active", Long.class);
        UUID equipoIdAntes = equipos.findByIdExterno(600000L).orElseThrow().getId();
        UUID jugadorIdAntes = jugadores.findByIdExterno(700000L).orElseThrow().getId();

        assertThatThrownBy(servicio::sincronizar)
                .isInstanceOf(FootballDataException.class)
                .hasMessage("La respuesta del proveedor no forma un snapshot utilizable.");

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM equipos WHERE external_id IS NOT NULL", Long.class))
                .isEqualTo(equiposAntes);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM jugadores WHERE external_id IS NOT NULL", Long.class))
                .isEqualTo(jugadoresAntes);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM equipos WHERE external_id IS NOT NULL AND is_active", Long.class))
                .isEqualTo(equiposActivosAntes);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM jugadores WHERE external_id IS NOT NULL AND is_active", Long.class))
                .isEqualTo(jugadoresActivosAntes);
        assertThat(equipos.findByIdExterno(600000L)).get()
                .satisfies(equipo -> {
                    assertThat(equipo.getId()).isEqualTo(equipoIdAntes);
                    assertThat(equipo.isActivo()).isTrue();
                });
        assertThat(jugadores.findByIdExterno(700000L)).get()
                .satisfies(jugador -> {
                    assertThat(jugador.getId()).isEqualTo(jugadorIdAntes);
                    assertThat(jugador.isActivo()).isTrue();
                });
        assertThat(jugadores.contar(null, null, null)).isEqualTo(jugadoresActivosAntes);
        assertThat(duplicados("equipos")).isZero();
        assertThat(duplicados("jugadores")).isZero();
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

    private static CatalogoSincronizado snapshot() {
        List<String> ligas = List.of("Premier League", "LaLiga", "Bundesliga", "Serie A", "Ligue 1");
        List<Integer> cantidades = List.of(20, 20, 18, 20, 18);
        List<Equipo> equiposSnapshot = new ArrayList<>();
        List<Jugador> jugadoresSnapshot = new ArrayList<>();
        int indice = 0;
        for (int liga = 0; liga < ligas.size(); liga++) {
            for (int i = 0; i < cantidades.get(liga); i++) {
                UUID equipoId = UUID.randomUUID();
                equiposSnapshot.add(Equipo.importado(equipoId, 600000L + indice,
                        "Equipo " + indice, "E", "E", ligas.get(liga), null,
                        2026L, null, null, AHORA));
                jugadoresSnapshot.add(Jugador.importado(UUID.randomUUID(), 700000L + indice,
                        "Jugador " + indice, equipoId, "Forward", null, "Argentina", AHORA));
                indice++;
            }
        }
        return new CatalogoSincronizado(equiposSnapshot, jugadoresSnapshot);
    }
}
