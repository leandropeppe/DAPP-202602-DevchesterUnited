package com.devchesterunited.devchesterunitedv2.integration.jugador;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.FamiliaEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.SnapshotEstadisticasJugador;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.IdentidadEstadisticaJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.RegistroEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.IdentidadExternaEquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.entity.EstadisticasSyncEntity;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.repository.EstadisticasJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.repository.EstadisticasSyncRepository;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.repository.EstadisticasSyncStagingRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.IdentidadExternaJugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.IdentidadExternaJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.scraping.estadisticas.ProveedorEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.service.impl.jugador.IdentidadEquipoResolver;
import com.devchesterunited.devchesterunitedv2.service.impl.jugador.IdentidadJugadorResolver;
import com.devchesterunited.devchesterunitedv2.service.interfaces.estadisticas.SincronizacionEstadisticasJugadorService;
import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
class IdentidadPersistidaWhoScoredIntegrationTest extends PostgresTestSupport {
    @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private ProveedorEstadisticasJugador proveedor;
    @Autowired private IdentidadJugadorResolver resolver;
    @Autowired private SincronizacionEstadisticasJugadorService servicio;
    @Autowired private JugadorRepository jugadores;
    @Autowired private EquipoRepository equiposRepositorio;
    @Autowired private IdentidadExternaJugadorRepository identidades;
    @Autowired private IdentidadExternaEquipoRepository identidadesEquipos;
    @Autowired private EstadisticasJugadorRepository estadisticas;
    @Autowired private EstadisticasSyncRepository sincronizaciones;
    @Autowired private EstadisticasSyncStagingRepository staging;
    @MockitoSpyBean private IdentidadEquipoResolver equipos;
    private UUID jugadorId;
    private final Set<UUID> fixturePlayers = new HashSet<>();
    private final Set<UUID> fixtureTeams = new HashSet<>();
    private final Set<UUID> fixtureSyncs = new HashSet<>();

    @AfterEach
    void limpiar() {
        fixturePlayers.forEach(id -> estadisticas.findByJugadorId(id).ifPresent(estadisticas::delete));
        sincronizaciones.findAll().stream().map(e -> e.getId()).filter(fixtureSyncs::contains)
                .forEach(id -> {
                    staging.findAllBySyncId(id).forEach(staging::delete);
                    sincronizaciones.deleteById(id);
                });
        identidades.findAllByProveedor(ProveedorIdentidadExterna.WHOSCORED).stream()
                .filter(identity -> fixturePlayers.contains(identity.getJugador().getId()))
                .forEach(identidades::delete);
        identidadesEquipos.findAllByProveedor(ProveedorIdentidadExterna.WHOSCORED).stream()
                .filter(identity -> fixtureTeams.contains(identity.getEquipo().getId()))
                .forEach(identidadesEquipos::delete);
        if (jugadorId != null && !fixturePlayers.contains(jugadorId)) {
            identidades.findByJugador_IdAndProveedor(jugadorId, ProveedorIdentidadExterna.WHOSCORED)
                    .ifPresent(identidades::delete);
            jugadores.deleteById(jugadorId);
        }
        fixturePlayers.forEach(jugadores::deleteById);
        fixtureTeams.forEach(equiposRepositorio::deleteById);
    }

    @Test
    @Transactional
    void reutilizaIdentidadPersistidaSinEjecutarMatchingInicial() {
        jugadorId = UUID.randomUUID();
        UUID equipoId = jugadores.findAll().stream().findFirst().orElseThrow().getEquipoId();
        var jugador = jugadores.saveAndFlush(new JugadorEntity(jugadorId, "Jugador Persistido",
                equipoId, "Forward", null, "Argentina", true));
        identidades.saveAndFlush(new IdentidadExternaJugadorEntity(UUID.randomUUID(), jugador,
                ProveedorIdentidadExterna.FOOTBALL_DATA, 9911L));
        identidades.saveAndFlush(new IdentidadExternaJugadorEntity(UUID.randomUUID(), jugador,
                ProveedorIdentidadExterna.WHOSCORED, 9911L));

        var equipo = equiposRepositorio.findById(equipoId).orElseThrow();
        var resultado = resolver.resolver(new IdentidadEstadisticaJugador(9911L, "Otro nombre",
                null, null, 1234L, "Otro equipo"), equipo, CompeticionEstadistica.PREMIER_LEAGUE);

        assertThat(resultado.jugadorId()).isEqualTo(jugadorId);
        assertThat(resultado.identidadNueva()).isNull();
        assertThat(identidades.findAllByProveedorAndExternalIdIn(ProveedorIdentidadExterna.WHOSCORED,
                java.util.List.of(9911L))).hasSize(1);
    }

    @Test
    void sincronizacionPosteriorReutilizaIdentidadSinMatchingNiNuevaIdentidad() {
        configurarFixtureCompleto();
        reset(equipos);
        Set<UUID> sincronizacionesPrevias = sincronizaciones.findAll().stream()
                .map(EstadisticasSyncEntity::getId).collect(java.util.stream.Collectors.toSet());

        servicio.sincronizar();
        sincronizaciones.findAll().stream().map(EstadisticasSyncEntity::getId)
                .filter(id -> !sincronizacionesPrevias.contains(id)).forEach(fixtureSyncs::add);

        verify(equipos, times(5)).resolver(any(), any(), any());
        assertThat(identidades.findAllByProveedor(ProveedorIdentidadExterna.WHOSCORED).stream()
                .filter(identity -> fixturePlayers.contains(identity.getJugador().getId()))).hasSize(5);
        assertThat(identidades.findAllByProveedor(ProveedorIdentidadExterna.WHOSCORED).stream()
                .filter(identity -> identity.getExternalId() == 98101L)).hasSize(1);
        assertThat(estadisticas.findByJugadorId(jugadorId)).isPresent();
    }

    private void configurarFixtureCompleto() {
        for (CompeticionEstadistica competicion : CompeticionEstadistica.values()) {
            int index = competicion.ordinal();
            Instant ahora = Instant.now();
            EquipoEntity equipo = equiposRepositorio.save(EquipoEntity.builder().id(UUID.randomUUID())
                    .nombre(nombreLiga(competicion) + " persistido US2").liga(nombreLiga(competicion))
                    .activo(true).creadoEn(ahora).actualizadoEn(ahora).build());
            fixtureTeams.add(equipo.getId());
            identidadesEquipos.save(new IdentidadExternaEquipoEntity(UUID.randomUUID(), equipo,
                    ProveedorIdentidadExterna.WHOSCORED, 98201L + index));
            JugadorEntity jugador = jugadores.save(new JugadorEntity(UUID.randomUUID(),
                    "Jugador persistido US2 " + index, equipo.getId(), "Forward", null,
                    "Argentina", true));
            fixturePlayers.add(jugador.getId());
            if (index == 0) {
                jugadorId = jugador.getId();
                identidades.save(new IdentidadExternaJugadorEntity(UUID.randomUUID(), jugador,
                        ProveedorIdentidadExterna.WHOSCORED, 98101L));
            }
        }
        configurarProveedorCompleto();
    }

    private void configurarProveedorCompleto() {
        doAnswer(invocation ->
                java.util.Arrays.stream(CompeticionEstadistica.values()).map(competicion -> {
                    int index = competicion.ordinal();
                    long playerId = index == 0 ? 98101L : 98101L + index;
                    String nombre = index == 0 ? "Nombre que no participa en matching"
                            : "Jugador persistido US2 " + index;
                    String nombreEquipo = index == 0 ? "Equipo que no participa en matching"
                            : nombreLiga(competicion) + " persistido US2";
                    var registro = new RegistroEstadisticasJugador(
                            new IdentidadEstadisticaJugador(playerId, nombre, "Forward", "Argentina",
                                    98201L + index, nombreEquipo),
                            com.devchesterunited.devchesterunitedv2.modelo.estadisticas.FamiliaEstadistica.GENERAL,
                            new MetricasEstadisticasJugador(90, 1, 1, BigDecimal.ONE,
                                    null, null, null, null, null, null));
                    return new com.devchesterunited.devchesterunitedv2.modelo.estadisticas.SnapshotEstadisticasJugador(
                            competicion, java.util.List.of(registro));
                }).toList()).when(proveedor).obtenerSnapshots(any());
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
