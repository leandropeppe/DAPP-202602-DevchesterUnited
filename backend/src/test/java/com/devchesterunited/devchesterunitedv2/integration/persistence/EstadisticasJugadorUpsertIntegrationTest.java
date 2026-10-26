package com.devchesterunited.devchesterunitedv2.integration.persistence;


import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.IdentidadEstadisticaJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.RegistroEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.IdentidadExternaEquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.repository.EstadisticasJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.repository.EstadisticasSyncRepository;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.repository.EstadisticasSyncStagingRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.IdentidadExternaJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.scraping.estadisticas.ProveedorEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.service.interfaces.estadisticas.SincronizacionEstadisticasJugadorService;
import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
class EstadisticasJugadorUpsertIntegrationTest extends PostgresTestSupport {
    @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private ProveedorEstadisticasJugador proveedor;
    @Autowired private SincronizacionEstadisticasJugadorService servicio;
    @Autowired private EstadisticasJugadorRepository estadisticas;
    @Autowired private EstadisticasSyncRepository sincronizaciones;
    @Autowired private EstadisticasSyncStagingRepository staging;
    @Autowired private IdentidadExternaJugadorRepository identidadesJugadores;
    @Autowired private IdentidadExternaEquipoRepository identidadesEquipos;
    @Autowired private JugadorRepository jugadores;
    @Autowired private EquipoRepository equipos;

    private final Set<UUID> fixtureTeams = new HashSet<>();
    private final Set<UUID> fixturePlayers = new HashSet<>();
    private final Set<UUID> fixtureSyncs = new HashSet<>();
    private final Set<UUID> baselineSyncs = new HashSet<>();
    private int versionMetricas;

    @BeforeEach
    void preparar() {
        baselineSyncs.addAll(sincronizaciones.findAll().stream().map(e -> e.getId()).toList());
        for (CompeticionEstadistica competicion : CompeticionEstadistica.values()) {
            int index = competicion.ordinal();
            Instant ahora = Instant.now();
            EquipoEntity equipo = equipos.save(EquipoEntity.builder().id(UUID.randomUUID())
                    .nombre(nombreLiga(competicion) + " US2 " + index)
                    .liga(nombreLiga(competicion)).activo(true).creadoEn(ahora)
                    .actualizadoEn(ahora).build());
            fixtureTeams.add(equipo.getId());
            identidadesEquipos.save(new IdentidadExternaEquipoEntity(UUID.randomUUID(), equipo,
                    ProveedorIdentidadExterna.WHOSCORED, 91001L + index));
            JugadorEntity jugador = jugadores.save(new JugadorEntity(UUID.randomUUID(),
                    "Jugador US2 " + index, equipo.getId(), "Forward", null, "Argentina", true));
            fixturePlayers.add(jugador.getId());
        }
        configurarProveedor();
    }

    @AfterEach
    void limpiar() {
        fixturePlayers.forEach(id -> estadisticas.findByJugadorId(id).ifPresent(estadisticas::delete));
        sincronizaciones.findAll().stream().map(e -> e.getId())
                .filter(id -> !baselineSyncs.contains(id)).forEach(fixtureSyncs::add);
        fixtureSyncs.forEach(id -> {
            staging.findAllBySyncId(id).forEach(staging::delete);
            sincronizaciones.deleteById(id);
        });
        identidadesJugadores.findAllByProveedor(ProveedorIdentidadExterna.WHOSCORED).stream()
                .filter(identity -> fixturePlayers.contains(identity.getJugador().getId()))
                .forEach(identidadesJugadores::delete);
        identidadesEquipos.findAllByProveedor(ProveedorIdentidadExterna.WHOSCORED).stream()
                .filter(identity -> fixtureTeams.contains(identity.getEquipo().getId()))
                .forEach(identidadesEquipos::delete);
        fixturePlayers.forEach(jugadores::deleteById);
        fixtureTeams.forEach(equipos::deleteById);
    }

    @Test
    void sincronizacionesIdenticasYActualizacionMantienenUnaFilaLogica() {
        long jugadoresAntes = jugadores.count();
        long equiposAntes = equipos.count();
        int identidadesJugadoresAntes = identidadesJugadores.findAllByProveedor(
                ProveedorIdentidadExterna.WHOSCORED).size();
        int identidadesEquiposAntes = identidadesEquipos.findAllByProveedor(
                ProveedorIdentidadExterna.WHOSCORED).size();

        versionMetricas = 1;
        servicio.sincronizar();
        UUID primeraSync = nuevaSync();
        UUID jugadorId = fixturePlayers.stream().findFirst().orElseThrow();
        var primera = estadisticas.findByJugadorId(jugadorId).orElseThrow();
        UUID idLogico = primera.getId();
        assertThat(primera.getSyncId()).isEqualTo(primeraSync);
        assertThat(jugadores.count()).isEqualTo(jugadoresAntes);
        assertThat(equipos.count()).isEqualTo(equiposAntes);
        assertThat(identidadesJugadores.findAllByProveedor(ProveedorIdentidadExterna.WHOSCORED))
                .hasSize(identidadesJugadoresAntes + 5);
        assertThat(identidadesEquipos.findAllByProveedor(ProveedorIdentidadExterna.WHOSCORED))
                .hasSize(identidadesEquiposAntes);
        assertThat(estadisticas.findAll().stream().filter(s -> fixturePlayers.contains(s.getJugadorId())))
                .hasSize(5);

        versionMetricas = 1;
        servicio.sincronizar();
        UUID segundaSync = nuevaSync();
        var segunda = estadisticas.findByJugadorId(jugadorId).orElseThrow();
        assertThat(segunda.getId()).isEqualTo(idLogico);
        assertThat(segunda.getSyncId()).isEqualTo(segundaSync).isNotEqualTo(primeraSync);
        assertThat(jugadores.count()).isEqualTo(jugadoresAntes);
        assertThat(equipos.count()).isEqualTo(equiposAntes);
        assertThat(identidadesJugadores.findAllByProveedor(ProveedorIdentidadExterna.WHOSCORED))
                .hasSize(identidadesJugadoresAntes + 5);
        assertThat(identidadesEquipos.findAllByProveedor(ProveedorIdentidadExterna.WHOSCORED))
                .hasSize(identidadesEquiposAntes);
        assertThat(estadisticas.findAll().stream().filter(s -> fixturePlayers.contains(s.getJugadorId())))
                .hasSize(5);

        versionMetricas = 2;
        servicio.sincronizar();
        UUID terceraSync = nuevaSync();
        var tercera = estadisticas.findByJugadorId(jugadorId).orElseThrow();
        assertThat(tercera.getId()).isEqualTo(idLogico);
        assertThat(tercera.getSyncId()).isEqualTo(terceraSync);
        assertThat(tercera.getMinutosJugados()).isEqualTo(120);
        assertThat(tercera.getGoles()).isEqualTo(3);
        assertThat(estadisticas.findAll().stream().filter(s -> fixturePlayers.contains(s.getJugadorId())))
                .hasSize(5);
    }

    private void configurarProveedor() {
        doAnswer(invocation ->
                java.util.Arrays.stream(CompeticionEstadistica.values()).map(competicion -> {
                    int index = competicion.ordinal();
                    var registro = new RegistroEstadisticasJugador(
                            new IdentidadEstadisticaJugador(90001L + index,
                                    "Jugador US2 " + index, "Forward", "Argentina",
                                    91001L + index, nombreLiga(competicion)),
                            com.devchesterunited.devchesterunitedv2.modelo.estadisticas.FamiliaEstadistica.GENERAL,
                            new MetricasEstadisticasJugador(versionMetricas == 2 ? 120 : 90,
                                    versionMetricas == 2 ? 3 : 2, 1, BigDecimal.ONE,
                                    null, null, null, null, null, null));
                    return new com.devchesterunited.devchesterunitedv2.modelo.estadisticas.SnapshotEstadisticasJugador(
                            competicion, java.util.List.of(registro));
                }).toList()).when(proveedor).obtenerSnapshots(any());
    }

    private UUID nuevaSync() {
        UUID id = sincronizaciones.findAll().stream().map(e -> e.getId())
                .filter(candidate -> !baselineSyncs.contains(candidate) && !fixtureSyncs.contains(candidate))
                .findFirst().orElseThrow();
        fixtureSyncs.add(id);
        return id;
    }

    private static String nombreLiga(CompeticionEstadistica competicion) {
        return switch (competicion) {
            case PREMIER_LEAGUE -> "Premier League";
            case LA_LIGA -> "La Liga";
            case SERIE_A -> "Serie A";
            case BUNDESLIGA -> "Bundesliga";
            case LIGUE_1 -> "Ligue 1";
        };
    }
}
