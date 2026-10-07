package com.devchesterunited.devchesterunitedv2.service;

import com.devchesterunited.devchesterunitedv2.adapter.ProveedorEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.FamiliaEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.IdentidadEstadisticaJugador;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.PaginaEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.RegistroEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.EstadisticasJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.EstadisticasSyncRepository;
import com.devchesterunited.devchesterunitedv2.persistence.EstadisticasSyncStagingRepository;
import com.devchesterunited.devchesterunitedv2.persistence.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.IdentidadExternaJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.service.interfaces.SincronizacionEstadisticasJugadorService;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class EnrolamientoIdentidadesWhoScoredIntegrationTest extends PostgresTestSupport {
    @MockBean private ProveedorEstadisticasJugador proveedor;
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

        assertThat(estadisticas.count()).isEqualTo(5);
        assertThat(identidadesJugadores.findAllByProveedor(ProveedorIdentidadExterna.WHOSCORED)).hasSize(5);
        assertThat(identidadesEquipos.findAllByProveedor(ProveedorIdentidadExterna.WHOSCORED)).hasSize(5);
        assertThat(ejecuciones.findAllByEstado(com.devchesterunited.devchesterunitedv2.modelo.EstadoEstadisticasSync.PUBLICADA))
                .isNotEmpty();
    }

    @Test
    void unFalloDePaginaNoPublicaIdentidadesNuevas() {
        configurarFuente(true);
        assertThatThrownBy(servicio::sincronizar).isInstanceOf(RuntimeException.class);
        assertThat(estadisticas.count()).isZero();
        assertThat(identidadesJugadores.findAllByProveedor(ProveedorIdentidadExterna.WHOSCORED)).isEmpty();
        assertThat(identidadesEquipos.findAllByProveedor(ProveedorIdentidadExterna.WHOSCORED)).isEmpty();
    }

    private void configurarFuente(boolean falla) {
        for (int i = 0; i < CompeticionEstadistica.values().length; i++) {
            var competencia = CompeticionEstadistica.values()[i];
            var equipo = equipo(competencia, i);
            equipos.save(equipo);
            fixtureTeams.add(equipo.getId());
            var player = new JugadorEntity(UUID.randomUUID(), "Jugador " + i, equipo.getId(),
                    "Forward", null, "Argentina", true);
            jugadores.save(player);
            fixturePlayers.add(player.getId());
        }
        when(proveedor.obtenerPagina(any(), any(), any(Integer.class), any(Integer.class)))
                .thenAnswer(invocation -> {
                    CompeticionEstadistica competicion = invocation.getArgument(0);
                    FamiliaEstadistica familia = invocation.getArgument(1);
                    int pagina = invocation.getArgument(2);
                    if (falla && competicion == CompeticionEstadistica.SERIE_A
                            && familia == FamiliaEstadistica.OFENSIVA && pagina == 1) {
                        throw new IllegalStateException("fallo controlado");
                    }
                    int index = competicion.ordinal();
                    var registro = new RegistroEstadisticasJugador(
                            new IdentidadEstadisticaJugador(10_000L + index, "Jugador " + index,
                                    "Forward", "Argentina", 20_000L + index,
                                    nombreLiga(competicion)), familia,
                            new MetricasEstadisticasJugador(90, 1, 2, BigDecimal.ONE, BigDecimal.TWO,
                                    BigDecimal.ONE, BigDecimal.ONE, BigDecimal.valueOf(7), 0, 0));
                    var registros = pagina == 1 ? List.of(registro) : List.<RegistroEstadisticasJugador>of();
                    return new PaginaEstadisticasJugador(competicion, familia, pagina, 50, 2, 1, registros);
                });
    }

    private static EquipoEntity equipo(CompeticionEstadistica competencia, int index) {
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
