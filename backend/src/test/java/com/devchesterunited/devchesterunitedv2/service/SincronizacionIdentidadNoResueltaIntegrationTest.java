package com.devchesterunited.devchesterunitedv2.service;

import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.IdentidadEstadisticaJugador;
import com.devchesterunited.devchesterunitedv2.persistence.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.EstadisticasJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.EstadisticasSyncRepository;
import com.devchesterunited.devchesterunitedv2.persistence.EstadisticasSyncStagingRepository;
import com.devchesterunited.devchesterunitedv2.persistence.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.IdentidadExternaJugadorRepository;
import com.devchesterunited.devchesterunitedv2.service.exception.IdentidadEstadisticaNoResueltaException;
import com.devchesterunited.devchesterunitedv2.service.impl.IdentidadEquipoResolver;
import com.devchesterunited.devchesterunitedv2.service.impl.IdentidadJugadorResolver;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EstadisticasJugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EstadisticasSyncEntity;
import com.devchesterunited.devchesterunitedv2.service.interfaces.SincronizacionEstadisticasJugadorService;
import com.devchesterunited.devchesterunitedv2.adapter.ProveedorEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.EstadoEstadisticasSync;
import com.devchesterunited.devchesterunitedv2.modelo.FamiliaEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.PaginaEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.RegistroEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.IdentidadEstadisticaJugador;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.mockito.Answers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;

@SpringBootTest
@ActiveProfiles("test")
class SincronizacionIdentidadNoResueltaIntegrationTest extends PostgresTestSupport {
    @MockBean(answer = Answers.CALLS_REAL_METHODS) private ProveedorEstadisticasJugador proveedor;
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
                com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna.WHOSCORED)
                .ifPresent(identidadesJugadores::delete));
        fixtureJugadores.forEach(jugadores::deleteById);
        fixtureEquipos.forEach(id -> identidades.findByEquipo_IdAndProveedor(id,
                com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna.WHOSCORED)
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

        assertThatThrownBy(() -> jugadoresResolver.resolver(hint, CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(IdentidadEstadisticaNoResueltaException.class);
        assertThat(identidadesJugadores.findByProveedorAndExternalId(
                com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna.WHOSCORED, 901L))
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

        equiposResolver.resolver(hint, CompeticionEstadistica.PREMIER_LEAGUE);
        assertThatThrownBy(() -> jugadoresResolver.resolver(hint, CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(IdentidadEstadisticaNoResueltaException.class);
    }

    @Test
    void ceroCandidatosEnElCasoDeUsoMarcaFallidaYConservaSnapshot() {
        var baseline = crearSnapshotAnterior("Baseline Zero FC", "Baseline Zero Player", 90110L);
        var snapshotPublicadoAntes = estadisticas.findAll().stream()
                .map(this::snapshotPublicado)
                .toList();
        var syncsAntes = ejecuciones.findAll().stream().map(e -> e.getId()).collect(java.util.stream.Collectors.toSet());
        when(proveedor.obtenerPagina(any(), any(), any(Integer.class), any(Integer.class)))
                .thenAnswer(invocacion -> pagina(invocacion.getArgument(0), invocacion.getArgument(1),
                        90111L, "Jugador inexistente", 90112L, "Equipo inexistente"));

        org.assertj.core.api.Assertions.assertThatThrownBy(servicio::sincronizar)
                .isInstanceOf(RuntimeException.class);

        assertThat(estadisticas.findByJugadorId(baseline.jugadorId()).orElseThrow().getSyncId())
                .isEqualTo(baseline.syncId());
        assertThat(estadisticas.findAll().stream().map(this::snapshotPublicado).toList())
                .containsExactlyInAnyOrderElementsOf(snapshotPublicadoAntes);
        assertThat(identidadesJugadores.findByProveedorAndExternalId(
                com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna.WHOSCORED, 90111L))
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
        var baseline = crearSnapshotAnterior("Baseline Multiple FC", "Baseline Multiple Player", 90210L);
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
        when(proveedor.obtenerPagina(any(), any(), any(Integer.class), any(Integer.class)))
                .thenAnswer(invocacion -> pagina(invocacion.getArgument(0), invocacion.getArgument(1),
                        90211L, "Ambiguous Player", 90212L, "Ambiguous Service FC"));

        org.assertj.core.api.Assertions.assertThatThrownBy(servicio::sincronizar)
                .isInstanceOf(RuntimeException.class);

        assertThat(estadisticas.findByJugadorId(baseline.jugadorId()).orElseThrow().getSyncId())
                .isEqualTo(baseline.syncId());
        assertThat(identidades.findByProveedorAndExternalId(
                com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna.WHOSCORED, 90211L))
                .isEmpty();
        assertThat(ejecuciones.findAllByEstado(EstadoEstadisticasSync.FALLIDA)).isNotEmpty();
        assertThat(staging.findAll()).hasSize(stagingAntes);
        ejecuciones.findAll().stream().map(e -> e.getId()).filter(id -> !syncsAntes.contains(id))
                .forEach(fixtureSyncs::add);
    }

    private Snapshot crearSnapshotAnterior(String nombreEquipo, String nombreJugador, long syncNumero) {
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
        estadisticas.save(new EstadisticasJugadorEntity(UUID.randomUUID(), jugador.getId(), "Forward", 70,
                1, 1, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.valueOf(6), 0, 0, syncId, now, now));
        return new Snapshot(jugador.getId(), syncId);
    }

    private PaginaEstadisticasJugador pagina(CompeticionEstadistica competicion,
            FamiliaEstadistica familia, long playerId, String playerName, long teamId, String teamName) {
        return new PaginaEstadisticasJugador(competicion, familia, 1, 50, 1, 1,
                java.util.List.of(new RegistroEstadisticasJugador(
                        new IdentidadEstadisticaJugador(playerId, playerName, "Forward", "Argentina",
                                teamId, teamName), familia,
                        new MetricasEstadisticasJugador(100, 2, 2, BigDecimal.ONE, BigDecimal.ONE,
                                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.valueOf(8), 0, 0))));
    }

    private record Snapshot(UUID jugadorId, UUID syncId) { }

    private record PublishedStats(UUID id, UUID jugadorId, String posicion, Integer minutosJugados,
            Integer goles, Integer asistencias, BigDecimal tiros, BigDecimal pasesClave,
            BigDecimal regates, BigDecimal tackles, BigDecimal valoracion, Integer tarjetasAmarillas,
            Integer tarjetasRojas, UUID syncId, Instant creadoEn, Instant actualizadoEn) { }
}
