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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class SincronizacionCatalogoIntegrationTest extends PostgresTestSupport {
    private static final Instant AHORA = Instant.parse("2026-09-16T12:00:00Z");
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
    void snapshotValidoPublica96EquiposDistribuidosYDesactivaDemo() {
        CatalogoSincronizado snapshot = snapshot(700000L, 800000L);
        when(adapter.sincronizar()).thenReturn(snapshot);

        var resultado = servicio.sincronizar();

        assertThat(resultado.equiposProcesados()).isEqualTo(96);
        assertThat(resultado.jugadoresProcesados()).isEqualTo(96);
        var activos = equipos.findAllByActivoTrue().stream().filter(e -> e.getIdExterno() != null).toList();
        assertThat(activos).hasSize(96);
        assertThat(activos.stream().filter(e -> e.getLiga().equals("Premier League")).count()).isEqualTo(20);
        assertThat(activos.stream().filter(e -> e.getLiga().equals("LaLiga")).count()).isEqualTo(20);
        assertThat(activos.stream().filter(e -> e.getLiga().equals("Bundesliga")).count()).isEqualTo(18);
        assertThat(activos.stream().filter(e -> e.getLiga().equals("Serie A")).count()).isEqualTo(20);
        assertThat(activos.stream().filter(e -> e.getLiga().equals("Ligue 1")).count()).isEqualTo(18);
        assertThat(jugadores.findAll()).filteredOn(j -> j.getIdExterno() == null).allMatch(j -> !j.isActivo());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM equipos WHERE external_id IS NOT NULL AND is_active", Long.class)).isEqualTo(96L);
    }

    @Test
    void segundaPublicacionHaceUpsertYReactivaSinDuplicar() {
        CatalogoSincronizado primero = snapshot(710000L, 810000L);
        when(adapter.sincronizar()).thenReturn(primero);
        servicio.sincronizar();
        UUID equipoId = equipos.findByIdExterno(710000L).orElseThrow().getId();
        UUID jugadorId = jugadores.findByIdExterno(810000L).orElseThrow().getId();
        jdbc.update("UPDATE equipos SET is_active = false WHERE external_id = 710000");
        jdbc.update("UPDATE jugadores SET is_active = false WHERE external_id = 810000");

        servicio.sincronizar();

        assertThat(equipos.findByIdExterno(710000L).orElseThrow().getId()).isEqualTo(equipoId);
        assertThat(jugadores.findByIdExterno(810000L).orElseThrow().getId()).isEqualTo(jugadorId);
        assertThat(equipos.findByIdExterno(710000L).orElseThrow().isActivo()).isTrue();
        assertThat(jugadores.findByIdExterno(810000L).orElseThrow().isActivo()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM equipos WHERE external_id IS NOT NULL", Long.class)).isEqualTo(96L);
    }

    @Test
    void falloDelProveedorConservaElSnapshotAnterior() {
        when(adapter.sincronizar()).thenReturn(snapshot(720000L, 820000L));
        servicio.sincronizar();
        long equiposAntes = jdbc.queryForObject("SELECT count(*) FROM equipos WHERE external_id IS NOT NULL AND is_active", Long.class);
        long jugadoresAntes = jdbc.queryForObject("SELECT count(*) FROM jugadores WHERE external_id IS NOT NULL AND is_active", Long.class);
        when(adapter.sincronizar()).thenThrow(new FootballDataException("fallo simulado"));

        assertThatThrownBy(servicio::sincronizar).isInstanceOf(FootballDataException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM equipos WHERE external_id IS NOT NULL AND is_active", Long.class)).isEqualTo(equiposAntes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM jugadores WHERE external_id IS NOT NULL AND is_active", Long.class)).isEqualTo(jugadoresAntes);
    }

    @Test
    void lockGlobalRechazaAlCompetidorSinConsultarAdapter() throws Exception {
        var iniciado = new CountDownLatch(1);
        var continuar = new CountDownLatch(1);
        when(adapter.sincronizar()).thenAnswer(invocacion -> {
            iniciado.countDown();
            assertThat(continuar.await(10, TimeUnit.SECONDS)).isTrue();
            return snapshot(730000L, 830000L);
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var primero = pool.submit(servicio::sincronizar);
            assertThat(iniciado.await(10, TimeUnit.SECONDS)).isTrue();
            var segundo = pool.submit(servicio::sincronizar);
            assertThatThrownBy(segundo::get).hasCauseInstanceOf(
                    com.devchesterunited.devchesterunitedv2.service.exception.SincronizacionEnCursoException.class);
            continuar.countDown();
            assertThat(primero.get(20, TimeUnit.SECONDS).equiposProcesados()).isEqualTo(96);
        }
    }

    private static CatalogoSincronizado snapshot(long equipoBase, long jugadorBase) {
        List<String> ligas = List.of("Premier League", "LaLiga", "Bundesliga", "Serie A", "Ligue 1");
        List<Integer> cantidades = List.of(20, 20, 18, 20, 18);
        var equipos = new ArrayList<Equipo>();
        var jugadores = new ArrayList<Jugador>();
        int indice = 0;
        for (int liga = 0; liga < ligas.size(); liga++) for (int i = 0; i < cantidades.get(liga); i++) {
            UUID equipoId = UUID.randomUUID();
            equipos.add(Equipo.importado(equipoId, equipoBase + indice, "Equipo " + indice, "E", "E", ligas.get(liga), null,
                    2026, null, null, AHORA));
            jugadores.add(Jugador.importado(UUID.randomUUID(), jugadorBase + indice, "Jugador " + indice + " Apellido",
                    equipoId, "Forward", indice % 2 == 0 ? null : 9, "Argentina", AHORA));
            indice++;
        }
        return new CatalogoSincronizado(equipos, jugadores);
    }

}
