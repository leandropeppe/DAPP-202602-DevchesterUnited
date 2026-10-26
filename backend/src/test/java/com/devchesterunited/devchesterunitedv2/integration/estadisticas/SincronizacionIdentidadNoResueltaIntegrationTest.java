package com.devchesterunited.devchesterunitedv2.integration.estadisticas;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.EstadoEstadisticasSync;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.FamiliaEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.IdentidadEstadisticaJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.RegistroEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.SnapshotEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna;

import com.devchesterunited.devchesterunitedv2.exception.IdentidadEstadisticaNoResueltaException;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.entity.EstadisticasJugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.entity.EstadisticasSyncEntity;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.repository.EstadisticasJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.repository.EstadisticasSyncRepository;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.repository.EstadisticasSyncStagingRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@SpringBootTest
@ActiveProfiles("test")
class SincronizacionIdentidadNoResueltaIntegrationTest extends PostgresTestSupport {
    @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private ProveedorEstadisticasJugador proveedor;
    @Autowired private SincronizacionEstadisticasJugadorService servicio;
    @Autowired private EquipoRepository equipos;
    @Autowired private JugadorRepository jugadores;
    @Autowired private IdentidadExternaEquipoRepository identidades;
    @Autowired private IdentidadExternaJugadorRepository identidadesJugadores;
    @Autowired private IdentidadEquipoResolver equiposResolver;
    @Autowired private IdentidadJugadorResolver jugadoresResolver;
    @Autowired private EstadisticasJugadorRepository estadisticas;
    @Autowired private EstadisticasSyncRepository ejecuciones;
    @Autowired private EstadisticasSyncStagingRepository staging;
    private final java.util.Set<UUID> fixtureJugadores = new java.util.HashSet<>();
    private final java.util.Set<UUID> fixtureEquipos = new java.util.HashSet<>();
    private final java.util.Set<UUID> fixtureSyncs = new java.util.HashSet<>();

    @AfterEach
    void limpiarFixtures() {
        fixtureJugadores.forEach(id -> estadisticas.findByJugadorId(id).ifPresent(estadisticas::delete));
        fixtureSyncs.forEach(id -> staging.findAllBySyncId(id).forEach(staging::delete));
        fixtureSyncs.forEach(ejecuciones::deleteById);
        fixtureJugadores.forEach(id -> identidadesJugadores.findByJugador_IdAndProveedor(id,
                com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna.WHOSCORED)
                .ifPresent(identidadesJugadores::delete));
        fixtureJugadores.forEach(jugadores::deleteById);
        fixtureEquipos.forEach(id -> identidades.findByEquipo_IdAndProveedor(id,
                com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna.WHOSCORED)
                .ifPresent(identidades::delete));
        fixtureEquipos.forEach(equipos::deleteById);
        fixtureJugadores.clear();
        fixtureEquipos.clear();
        fixtureSyncs.clear();
    }

    @Test
    @Transactional
    void ceroCandidatosNoUsaMatchingFuzzyNiCreaIdentidad() {
        var hint = new IdentidadEstadisticaJugador(801L, "Nombre parecido", "Forward",
                "Argentina", 901L, "Equipo inexistente");
        var equipo = equipos.save(EquipoEntity.builder().id(UUID.randomUUID()).nombre("Equipo local")
                .liga("Premier League").activo(true).creadoEn(Instant.now()).actualizadoEn(Instant.now()).build());
        fixtureEquipos.add(equipo.getId());

        assertThatThrownBy(() -> jugadoresResolver.resolver(hint, equipo, CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(IdentidadEstadisticaNoResueltaException.class);
        assertThat(identidadesJugadores.findByProveedorAndExternalId(
                com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna.WHOSCORED, 901L))
                .isEmpty();
    }

    @Test
    @Transactional
    void multiplesCandidatosYEvidenciaIncompatibleBloqueanLaResolucion() {
        Instant now = Instant.now();
        var equipo = equipos.save(EquipoEntity.builder().id(UUID.randomUUID()).nombre("Equipo ambiguo")
                .liga("Premier League").activo(true).creadoEn(now).actualizadoEn(now).build());
        jugadores.save(new JugadorEntity(UUID.randomUUID(), "Alex Smith", equipo.getId(),
                "Forward", 9, "Argentina", true));
        jugadores.save(new JugadorEntity(UUID.randomUUID(), " Alex   Smith ", equipo.getId(),
                "Midfielder", 10, "Argentina", true));
        var hint = new IdentidadEstadisticaJugador(802L, "Alex Smith", "Defender",
                "Argentina", 902L, "Equipo ambiguo");

        assertThatThrownBy(() -> equiposResolver.resolver(902L, "Equipo ambiguo", CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(IdentidadEstadisticaNoResueltaException.class);
        assertThatThrownBy(() -> jugadoresResolver.resolver(hint, equipo, CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(IdentidadEstadisticaNoResueltaException.class);
    }

    @Test
    void ceroCandidatosEnElCasoDeUsoMarcaFallidaYConservaSnapshot() {
        var baseline = crearSnapshotAnterior("Baseline Zero FC", "Baseline Zero Player");
        var snapshotPublicadoAntes = estadisticas.findAll().stream()
                .map(this::snapshotPublicado)
                .toList();
        var syncsAntes = ejecuciones.findAll().stream().map(e -> e.getId()).collect(java.util.stream.Collectors.toSet());
        doReturn(snapshots(90111L, "Jugador inexistente",
                90112L, "Equipo inexistente")).when(proveedor).obtenerSnapshots(any());

        org.assertj.core.api.Assertions.assertThatThrownBy(servicio::sincronizar)
                .isInstanceOf(RuntimeException.class);

        assertThat(estadisticas.findByJugadorId(baseline.jugadorId()).orElseThrow().getSyncId())
                .isEqualTo(baseline.syncId());
        assertThat(estadisticas.findAll().stream().map(this::snapshotPublicado).toList())
                .containsExactlyInAnyOrderElementsOf(snapshotPublicadoAntes);
        assertThat(identidadesJugadores.findByProveedorAndExternalId(
                com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna.WHOSCORED, 90111L))
                .isEmpty();
        assertThat(ejecuciones.findAllByEstado(EstadoEstadisticasSync.FALLIDA)).isNotEmpty();
        ejecuciones.findAll().stream().map(e -> e.getId()).filter(id -> !syncsAntes.contains(id))
                .forEach(fixtureSyncs::add);
    }

    private PublishedStats snapshotPublicado(EstadisticasJugadorEntity estadistica) {
        return new PublishedStats(estadistica.getId(), estadistica.getJugadorId(), estadistica.getPosicion(),
                estadistica.getMinutosJugados(), estadistica.getGoles(), estadistica.getAsistencias(),
                estadistica.getTiros(), estadistica.getPasesClave(), estadistica.getRegates(),
                estadistica.getTackles(), estadistica.getValoracion(), estadistica.getTarjetasAmarillas(),
                estadistica.getTarjetasRojas(), estadistica.getSyncId(), estadistica.getCreadoEn(),
                estadistica.getActualizadoEn());
    }

    @Test
    void multiplesCandidatosEnElCasoDeUsoBloqueanPublicacion() {
        var baseline = crearSnapshotAnterior("Baseline Multiple FC", "Baseline Multiple Player");
        var syncsAntes = ejecuciones.findAll().stream().map(e -> e.getId()).collect(java.util.stream.Collectors.toSet());
        int stagingAntes = staging.findAll().size();
        Instant now = Instant.now();
        var equipo = equipos.save(EquipoEntity.builder().id(UUID.randomUUID()).nombre("Ambiguous Service FC")
                .liga("Premier League").activo(true).creadoEn(now).actualizadoEn(now).build());
        var ambiguoUno = jugadores.save(new JugadorEntity(UUID.randomUUID(), "Ambiguous Player", equipo.getId(),
                "Forward", 9, "Argentina", true));
        var ambiguoDos = jugadores.save(new JugadorEntity(UUID.randomUUID(), "Ambiguous Player", equipo.getId(),
                "Forward", 10, "Argentina", true));
        fixtureJugadores.add(ambiguoUno.getId());
        fixtureJugadores.add(ambiguoDos.getId());
        fixtureEquipos.add(equipo.getId());
        doReturn(snapshots(90211L, "Ambiguous Player",
                90212L, "Ambiguous Service FC")).when(proveedor).obtenerSnapshots(any());

        org.assertj.core.api.Assertions.assertThatThrownBy(servicio::sincronizar)
                .isInstanceOf(RuntimeException.class);

        assertThat(estadisticas.findByJugadorId(baseline.jugadorId()).orElseThrow().getSyncId())
                .isEqualTo(baseline.syncId());
        assertThat(identidades.findByProveedorAndExternalId(
                com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna.WHOSCORED, 90211L))
                .isEmpty();
        assertThat(ejecuciones.findAllByEstado(EstadoEstadisticasSync.FALLIDA)).isNotEmpty();
        assertThat(staging.findAll()).hasSize(stagingAntes);
        ejecuciones.findAll().stream().map(e -> e.getId()).filter(id -> !syncsAntes.contains(id))
                .forEach(fixtureSyncs::add);
    }

    private Snapshot crearSnapshotAnterior(String nombreEquipo, String nombreJugador) {
        Instant now = Instant.now();
        var equipo = equipos.save(EquipoEntity.builder().id(UUID.randomUUID()).nombre(nombreEquipo)
                .liga("Premier League").activo(true).creadoEn(now).actualizadoEn(now).build());
        fixtureEquipos.add(equipo.getId());
        var jugador = jugadores.save(new JugadorEntity(UUID.randomUUID(), nombreJugador, equipo.getId(),
                "Forward", 9, "Argentina", true));
        fixtureJugadores.add(jugador.getId());
        UUID syncId = UUID.randomUUID();
        fixtureSyncs.add(syncId);
        ejecuciones.save(new EstadisticasSyncEntity(syncId, EstadoEstadisticasSync.PUBLICADA, now));
        estadisticas.save(new EstadisticasJugadorEntity(EstadisticasJugadorEntity.Creacion.builder()
                .id(UUID.randomUUID()).jugadorId(jugador.getId()).posicion("Forward").minutosJugados(70)
                .goles(1).asistencias(1).tiros(BigDecimal.ONE).pasesClave(BigDecimal.ONE)
                .regates(BigDecimal.ONE).tackles(BigDecimal.ONE).valoracion(BigDecimal.valueOf(6))
                .tarjetasAmarillas(0).tarjetasRojas(0).syncId(syncId).creadoEn(now).actualizadoEn(now)));
        return new Snapshot(jugador.getId(), syncId);
    }

    private List<com.devchesterunited.devchesterunitedv2.modelo.estadisticas.SnapshotEstadisticasJugador> snapshots(
            long playerId, String playerName, long teamId, String teamName) {
        return java.util.Arrays.stream(CompeticionEstadistica.values()).map(competicion ->
                new com.devchesterunited.devchesterunitedv2.modelo.estadisticas.SnapshotEstadisticasJugador(
                        competicion, java.util.List.of(new RegistroEstadisticasJugador(
                                new IdentidadEstadisticaJugador(playerId, playerName, "Forward", "Argentina",
                                        teamId, teamName),
                                com.devchesterunited.devchesterunitedv2.modelo.estadisticas.FamiliaEstadistica.GENERAL,
                                new MetricasEstadisticasJugador(100, 2, 2, BigDecimal.ONE, BigDecimal.ONE,
                                        BigDecimal.ONE, BigDecimal.ONE, BigDecimal.valueOf(8), 0, 0))))).toList();
    }

    private record Snapshot(UUID jugadorId, UUID syncId) { }

    private record PublishedStats(UUID id, UUID jugadorId, String posicion, Integer minutosJugados,
            Integer goles, Integer asistencias, BigDecimal tiros, BigDecimal pasesClave,
            BigDecimal regates, BigDecimal tackles, BigDecimal valoracion, Integer tarjetasAmarillas,
            Integer tarjetasRojas, UUID syncId, Instant creadoEn, Instant actualizadoEn) { }
}
