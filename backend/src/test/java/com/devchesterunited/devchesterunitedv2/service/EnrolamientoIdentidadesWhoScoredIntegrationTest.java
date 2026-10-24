package com.devchesterunited.devchesterunitedv2.service;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.Jugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.FamiliaEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.SnapshotEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.EstadoEstadisticasSync;

import com.devchesterunited.devchesterunitedv2.scraping.estadisticas.ProveedorEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.IdentidadEstadisticaJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.RegistroEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.repository.EstadisticasJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.repository.EstadisticasSyncRepository;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.repository.EstadisticasSyncStagingRepository;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.IdentidadExternaJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.IdentidadExternaEquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.service.interfaces.estadisticas.SincronizacionEstadisticasJugadorService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.mockito.Answers;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@SpringBootTest
@ActiveProfiles("test")
class EnrolamientoIdentidadesWhoScoredIntegrationTest extends PostgresTestSupport {
    @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private ProveedorEstadisticasJugador proveedor;
    @org.springframework.beans.factory.annotation.Autowired private SincronizacionEstadisticasJugadorService servicio;
    @org.springframework.beans.factory.annotation.Autowired private EquipoRepository equipos;
    @org.springframework.beans.factory.annotation.Autowired private JugadorRepository jugadores;
    @org.springframework.beans.factory.annotation.Autowired private IdentidadExternaEquipoRepository identidadesEquipos;
    @org.springframework.beans.factory.annotation.Autowired private IdentidadExternaJugadorRepository identidadesJugadores;
    @org.springframework.beans.factory.annotation.Autowired private EstadisticasJugadorRepository estadisticas;
    @org.springframework.beans.factory.annotation.Autowired private EstadisticasSyncRepository ejecuciones;
    @org.springframework.beans.factory.annotation.Autowired private EstadisticasSyncStagingRepository staging;
    private final Set<UUID> fixtureTeams = new HashSet<>();
    private final Set<UUID> fixturePlayers = new HashSet<>();
    private final Set<UUID> fixtureSyncs = new HashSet<>();
    private final Set<UUID> baselineSyncs = new HashSet<>();

    @BeforeEach
    void limpiar() {
        fixtureTeams.clear();
        fixturePlayers.clear();
        fixtureSyncs.clear();
        baselineSyncs.clear();
        ejecuciones.findAll().forEach(ejecucion -> baselineSyncs.add(ejecucion.getId()));
    }

    @AfterEach
    void limpiarDespues() {
        fixturePlayers.forEach(id -> estadisticas.findByJugadorId(id).ifPresent(estadisticas::delete));
        ejecuciones.findAll().stream().map(e -> e.getId())
                .filter(id -> !baselineSyncs.contains(id)).forEach(fixtureSyncs::add);
        fixtureSyncs.forEach(id -> {
            staging.findAllBySyncId(id).forEach(staging::delete);
            ejecuciones.deleteById(id);
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
    void nuevasIdentidadesYLasTresFamiliasSoloQuedanVisiblesTrasPromocion() {
        configurarFuente(false);
        servicio.sincronizar();

        assertThat(estadisticas.count()).isEqualTo(7);
        assertThat(identidadesJugadores.findAllByProveedor(ProveedorIdentidadExterna.WHOSCORED)).hasSize(7);
        assertThat(identidadesEquipos.findAllByProveedor(ProveedorIdentidadExterna.WHOSCORED)).hasSize(5);
        assertThat(ejecuciones.findAllByEstado(com.devchesterunited.devchesterunitedv2.modelo.estadisticas.EstadoEstadisticasSync.PUBLICADA))
                .isNotEmpty();
        verify(proveedor, times(1)).obtenerSnapshots(any());
        assertThat(estadisticas.findAll()).hasSize(7)
                .anySatisfy(stat -> assertThat(stat.getMinutosJugados()).isEqualTo(90))
                .anySatisfy(stat -> assertThat(stat.getMinutosJugados()).isEqualTo(120));
    }

    @Test
    void unFalloDePaginaNoPublicaIdentidadesNuevas() {
        configurarFuente(true);
        assertThatThrownBy(servicio::sincronizar).isInstanceOf(RuntimeException.class);
        assertThat(estadisticas.count()).isZero();
        assertThat(identidadesJugadores.findAllByProveedor(ProveedorIdentidadExterna.WHOSCORED)).isEmpty();
        assertThat(identidadesEquipos.findAllByProveedor(ProveedorIdentidadExterna.WHOSCORED)).hasSize(5);
        var ejecucionesFallidas = ejecuciones.findAllByEstado(
                com.devchesterunited.devchesterunitedv2.modelo.estadisticas.EstadoEstadisticasSync.FALLIDA);
        assertThat(ejecucionesFallidas).isNotEmpty();
        assertThat(ejecucionesFallidas.stream().allMatch(e -> staging.findAllBySyncId(e.getId()).isEmpty()))
                .isTrue();
    }

    private void configurarFuente(boolean falla) {
        for (int i = 0; i < CompeticionEstadistica.values().length; i++) {
            var competencia = CompeticionEstadistica.values()[i];
            var equipo = equipo(competencia);
            equipos.save(equipo);
            fixtureTeams.add(equipo.getId());
            identidadesEquipos.save(new IdentidadExternaEquipoEntity(UUID.randomUUID(), equipo,
                    ProveedorIdentidadExterna.WHOSCORED, 20_000L + i));
            var player = new JugadorEntity(UUID.randomUUID(), "Jugador " + i, equipo.getId(),
                    "Forward", null, "Argentina", true);
            jugadores.save(player);
            fixturePlayers.add(player.getId());
            if (i < 2) {
                var segundo = new JugadorEntity(UUID.randomUUID(), "Jugador " + i + " B", equipo.getId(),
                        "Midfielder", null, "Argentina", true);
                jugadores.save(segundo);
                fixturePlayers.add(segundo.getId());
            }
        }
        doAnswer(invocation -> {
            if (falla) throw new IllegalStateException("fallo controlado");
            return java.util.Arrays.stream(CompeticionEstadistica.values()).map(competicion -> {
                int index = competicion.ordinal();
                var primero = new RegistroEstadisticasJugador(
                        new IdentidadEstadisticaJugador(10_000L + index, "Jugador " + index,
                                "Forward", "Argentina", 20_000L + index, nombreLiga(competicion)),
                        com.devchesterunited.devchesterunitedv2.modelo.estadisticas.FamiliaEstadistica.GENERAL,
                        new MetricasEstadisticasJugador(90, 1, 2, BigDecimal.ONE, BigDecimal.TWO,
                                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.valueOf(7), 0, 0));
                var segundo = new RegistroEstadisticasJugador(
                        new IdentidadEstadisticaJugador(30_000L + index, "Jugador " + index + " B",
                                "Midfielder", "Argentina", 20_000L + index, nombreLiga(competicion)),
                        com.devchesterunited.devchesterunitedv2.modelo.estadisticas.FamiliaEstadistica.GENERAL,
                        new MetricasEstadisticasJugador(120, 3, 4, BigDecimal.TEN, BigDecimal.valueOf(6),
                                BigDecimal.valueOf(2), BigDecimal.valueOf(3), BigDecimal.valueOf(8), 1, 0));
                List<RegistroEstadisticasJugador> registros = index < 2
                        ? List.of(primero, segundo) : List.of(primero);
                return new com.devchesterunited.devchesterunitedv2.modelo.estadisticas.SnapshotEstadisticasJugador(
                        competicion, registros);
            }).toList();
        }).when(proveedor).obtenerSnapshots(any());
    }

    private static EquipoEntity equipo(CompeticionEstadistica competencia) {
        Instant now = Instant.now();
        return EquipoEntity.builder().id(UUID.randomUUID()).nombre(nombreLiga(competencia))
                .liga(nombreLiga(competencia)).activo(true).creadoEn(now).actualizadoEn(now).build();
    }

    private static String nombreLiga(CompeticionEstadistica competencia) {
        return switch (competencia) {
            case PREMIER_LEAGUE -> "Premier League";
            case LA_LIGA -> "La Liga";
            case SERIE_A -> "Serie A";
            case BUNDESLIGA -> "Bundesliga";
            case LIGUE_1 -> "Ligue 1";
        };
    }
}
