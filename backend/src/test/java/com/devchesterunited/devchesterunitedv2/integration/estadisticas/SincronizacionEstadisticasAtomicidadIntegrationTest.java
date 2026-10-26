package com.devchesterunited.devchesterunitedv2.integration.estadisticas;


import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.EstadoEstadisticasSync;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.IdentidadExternaEquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.entity.EstadisticasJugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.entity.EstadisticasSyncEntity;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.entity.EstadisticasSyncStagingEntity;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.repository.EstadisticasJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.repository.EstadisticasSyncRepository;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.repository.EstadisticasSyncStagingRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.IdentidadExternaJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.scraping.estadisticas.ProveedorEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.scraping.estadisticas.WhoScoredStatisticsException;
import com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.EstadisticasPromocionService;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
class SincronizacionEstadisticasAtomicidadIntegrationTest extends PostgresTestSupport {
    @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private ProveedorEstadisticasJugador proveedor;
    @Autowired private SincronizacionEstadisticasJugadorService servicio;
    @Autowired private EquipoRepository equipos;
    @Autowired private JugadorRepository jugadores;
    @Autowired private EstadisticasSyncRepository ejecuciones;
    @MockitoSpyBean private EstadisticasJugadorRepository estadisticas;
    @Autowired private EstadisticasSyncStagingRepository staging;
    @Autowired private IdentidadExternaEquipoRepository identidadesEquipos;
    @Autowired private IdentidadExternaJugadorRepository identidadesJugadores;
    @Autowired private EstadisticasPromocionService promocion;
    private final java.util.Set<UUID> fixtureJugadores = new java.util.HashSet<>();
    private final java.util.Set<UUID> fixtureEquipos = new java.util.HashSet<>();
    private final java.util.Set<UUID> fixtureSyncs = new java.util.HashSet<>();

    @AfterEach
    void limpiarFixtures() {
        identidadesEquipos.findByProveedorAndExternalId(ProveedorIdentidadExterna.WHOSCORED, 88101L)
                .ifPresent(identidadesEquipos::delete);
        fixtureJugadores.forEach(id -> estadisticas.findByJugadorId(id).ifPresent(estadisticas::delete));
        fixtureSyncs.forEach(id -> staging.findAllBySyncId(id).forEach(staging::delete));
        fixtureSyncs.forEach(ejecuciones::deleteById);
        fixtureJugadores.forEach(jugadores::deleteById);
        fixtureEquipos.forEach(equipos::deleteById);
        fixtureJugadores.clear();
        fixtureEquipos.clear();
        fixtureSyncs.clear();
    }

    @Test
    void falloDeProveedorConservaElSnapshotPublicadoAnterior() {
        Instant now = Instant.now();
        var equipo = equipos.save(EquipoEntity.builder().id(UUID.randomUUID()).nombre("Snapshot FC")
                .liga("Premier League").activo(true).creadoEn(now).actualizadoEn(now).build());
        fixtureEquipos.add(equipo.getId());
        var jugador = jugadores.save(new JugadorEntity(UUID.randomUUID(), "Snapshot Player",
                equipo.getId(), "Forward", 9, "Argentina", true));
        fixtureJugadores.add(jugador.getId());
        UUID publicada = UUID.randomUUID();
        fixtureSyncs.add(publicada);
        ejecuciones.save(new EstadisticasSyncEntity(publicada, EstadoEstadisticasSync.PUBLICADA, now));
        estadisticas.save(new EstadisticasJugadorEntity(EstadisticasJugadorEntity.Creacion.builder()
                .id(UUID.randomUUID()).jugadorId(jugador.getId()).posicion("Forward").minutosJugados(90)
                .goles(3).asistencias(1).tiros(BigDecimal.ONE).pasesClave(BigDecimal.ONE)
                .regates(BigDecimal.ONE).tackles(BigDecimal.ONE).valoracion(BigDecimal.valueOf(7))
                .tarjetasAmarillas(0).tarjetasRojas(0).syncId(publicada).creadoEn(now).actualizadoEn(now)));
        doThrow(new IllegalStateException("provider internal secret"))
                .when(proveedor).obtenerSnapshots(any());

        assertThatThrownBy(servicio::sincronizar).isInstanceOf(RuntimeException.class);

        var vigente = estadisticas.findByJugadorId(jugador.getId()).orElseThrow();
        assertThat(vigente.getSyncId()).isEqualTo(publicada);
        assertThat(vigente.getMinutosJugados()).isEqualTo(90);
        assertThat(ejecuciones.findAllByEstado(EstadoEstadisticasSync.FALLIDA)).isNotEmpty()
                .allSatisfy(e -> assertThat(e.getErrorTecnico()).doesNotContain("secret"));
    }

    @Test
    void WhoScoredExceptionSePersisteConErrorOperativoSanitizado() {
        Instant now = Instant.now();
        var equipo = equipos.save(EquipoEntity.builder().id(UUID.randomUUID()).nombre("Sanitized FC")
                .liga("Premier League").activo(true).creadoEn(now).actualizadoEn(now).build());
        fixtureEquipos.add(equipo.getId());
        var jugador = jugadores.save(new JugadorEntity(UUID.randomUUID(), "Sanitized Player",
                equipo.getId(), "Forward", 9, "Argentina", true));
        fixtureJugadores.add(jugador.getId());
        UUID anterior = UUID.randomUUID();
        fixtureSyncs.add(anterior);
        ejecuciones.save(new EstadisticasSyncEntity(anterior, EstadoEstadisticasSync.PUBLICADA, now));
        estadisticas.save(new EstadisticasJugadorEntity(EstadisticasJugadorEntity.Creacion.builder()
                .id(UUID.randomUUID()).jugadorId(jugador.getId()).posicion("Forward").minutosJugados(75)
                .goles(1).asistencias(1).tiros(BigDecimal.ONE).pasesClave(BigDecimal.ONE)
                .regates(BigDecimal.ONE).tackles(BigDecimal.ONE).valoracion(BigDecimal.valueOf(6))
                .tarjetasAmarillas(0).tarjetasRojas(0).syncId(anterior).creadoEn(now).actualizadoEn(now)));
        doThrow(new WhoScoredStatisticsException(
                        "https://whoscored.test/private?token=secret body=full-sensitive-body header=Bearer-secret",
                        new IllegalStateException("internal stack secret")))
                .when(proveedor).obtenerSnapshots(any());

        assertThatThrownBy(servicio::sincronizar).isInstanceOf(RuntimeException.class);

        var fallida = ejecuciones.findAllByEstado(EstadoEstadisticasSync.FALLIDA).stream()
                .filter(e -> !e.getId().equals(anterior)).findFirst().orElseThrow();
        assertThat(fallida.getErrorTecnico()).isEqualTo(
                "No fue posible completar la sincronización estadística.");
        assertThat(fallida.getErrorTecnico()).doesNotContain("whoscored.test", "secret", "Bearer", "stack");
        assertThat(estadisticas.findByJugadorId(jugador.getId()).orElseThrow().getSyncId()).isEqualTo(anterior);
        fixtureSyncs.add(fallida.getId());
    }

    @Test
    void falloDurantePromocionRevierteIdentidadesYEstadisticasJuntoConElEstado() {
        Instant now = Instant.now();
        var equipo = equipos.save(EquipoEntity.builder().id(UUID.randomUUID()).nombre("Atomic FC")
                .liga("Premier League").activo(true).creadoEn(now).actualizadoEn(now).build());
        fixtureEquipos.add(equipo.getId());
        var jugador = jugadores.save(new JugadorEntity(UUID.randomUUID(), "Atomic Player",
                equipo.getId(), "Forward", 9, "Argentina", true));
        fixtureJugadores.add(jugador.getId());
        UUID anterior = UUID.randomUUID();
        fixtureSyncs.add(anterior);
        ejecuciones.save(new EstadisticasSyncEntity(anterior, EstadoEstadisticasSync.PUBLICADA, now));
        estadisticas.save(new EstadisticasJugadorEntity(EstadisticasJugadorEntity.Creacion.builder()
                .id(UUID.randomUUID()).jugadorId(jugador.getId()).posicion("Forward").minutosJugados(88)
                .goles(2).asistencias(1).tiros(BigDecimal.ONE).pasesClave(BigDecimal.ONE)
                .regates(BigDecimal.ONE).tackles(BigDecimal.ONE).valoracion(BigDecimal.valueOf(6))
                .tarjetasAmarillas(0).tarjetasRojas(0).syncId(anterior).creadoEn(now).actualizadoEn(now)));

        UUID syncId = UUID.randomUUID();
        fixtureSyncs.add(syncId);
        ejecuciones.save(new EstadisticasSyncEntity(syncId, EstadoEstadisticasSync.VALIDADA, now));
        var fila = new EstadisticasSyncStagingEntity(UUID.randomUUID(), syncId, "PREMIER_LEAGUE", 99001L,
                "Atomic Player");
        fila.aplicar(aplicacion(jugador.getId(), equipo.getId(), "GENERAL").minutos(100).goles(4).asistencias(2)
                .tiros(BigDecimal.ONE).pasesClave(BigDecimal.ONE).regates(BigDecimal.ONE)
                .tackles(BigDecimal.ONE).valoracion(BigDecimal.valueOf(8)).amarillas(0).rojas(0));
        fila.aplicar(aplicacion(jugador.getId(), equipo.getId(), "OFENSIVA"));
        fila.aplicar(aplicacion(jugador.getId(), equipo.getId(), "DEFENSIVA"));
        staging.save(fila);

        doThrow(new IllegalStateException("persistencia controlada")).when(estadisticas)
                .save(any(EstadisticasJugadorEntity.class));
        var nuevoEquipo = new IdentidadEquipoResolver.NuevaIdentidad(equipo.getId(), 88001L, equipo);
        var nuevoJugador = new IdentidadJugadorResolver.NuevaIdentidad(jugador.getId(), 99001L, jugador);

        var jugadoresNuevos = java.util.List.of(nuevoJugador);
        var equiposNuevos = java.util.List.of(nuevoEquipo);
        var estadisticasNuevas = java.util.Map.<java.util.UUID, EstadisticasJugadorEntity>of();
        assertThatThrownBy(() -> promocion.promover(syncId, jugadoresNuevos,
                equiposNuevos, estadisticasNuevas))
                .isInstanceOf(RuntimeException.class);

        assertThat(estadisticas.findByJugadorId(jugador.getId()).orElseThrow().getSyncId()).isEqualTo(anterior);
        assertThat(identidadesEquipos.findByProveedorAndExternalId(
                com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna.WHOSCORED, 88001L))
                .isEmpty();
        assertThat(identidadesJugadores.findByProveedorAndExternalId(
                com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna.WHOSCORED, 99001L))
                .isEmpty();
        assertThat(ejecuciones.findById(syncId).orElseThrow().getEstado())
                .isEqualTo(EstadoEstadisticasSync.VALIDADA);
    }

    @Test
    void falloDurantePromocionDesdeElCasoDeUsoMarcaFallidaYConservaSnapshot() {
        var fallidasAntes = ejecuciones.findAllByEstado(EstadoEstadisticasSync.FALLIDA).stream()
                .map(e -> e.getId()).collect(java.util.stream.Collectors.toSet());
        Instant now = Instant.now();
        var equipo = equipos.save(EquipoEntity.builder().id(UUID.randomUUID()).nombre("Service Atomic FC")
                .liga("Premier League").activo(true).creadoEn(now).actualizadoEn(now).build());
        fixtureEquipos.add(equipo.getId());
        identidadesEquipos.save(new IdentidadExternaEquipoEntity(UUID.randomUUID(), equipo,
                ProveedorIdentidadExterna.WHOSCORED, 88101L));
        var jugador = jugadores.save(new JugadorEntity(UUID.randomUUID(), "Service Atomic Player",
                equipo.getId(), "Forward", 9, "Argentina", true));
        fixtureJugadores.add(jugador.getId());
        UUID anterior = UUID.randomUUID();
        fixtureSyncs.add(anterior);
        ejecuciones.save(new EstadisticasSyncEntity(anterior, EstadoEstadisticasSync.PUBLICADA, now));
        estadisticas.save(new EstadisticasJugadorEntity(EstadisticasJugadorEntity.Creacion.builder()
                .id(UUID.randomUUID()).jugadorId(jugador.getId()).posicion("Forward").minutosJugados(77)
                .goles(1).asistencias(1).tiros(BigDecimal.ONE).pasesClave(BigDecimal.ONE)
                .regates(BigDecimal.ONE).tackles(BigDecimal.ONE).valoracion(BigDecimal.valueOf(6))
                .tarjetasAmarillas(0).tarjetasRojas(0).syncId(anterior).creadoEn(now).actualizadoEn(now)));
        doReturn(
                java.util.Arrays.stream(CompeticionEstadistica.values()).map(competicion -> {
                    if (competicion != CompeticionEstadistica.PREMIER_LEAGUE) {
                        return new com.devchesterunited.devchesterunitedv2.modelo.estadisticas.SnapshotEstadisticasJugador(
                                competicion, java.util.List.of());
                    }
                    return new com.devchesterunited.devchesterunitedv2.modelo.estadisticas.SnapshotEstadisticasJugador(
                            competicion, java.util.List.of(
                                    new com.devchesterunited.devchesterunitedv2.modelo.estadisticas.RegistroEstadisticasJugador(
                                            new com.devchesterunited.devchesterunitedv2.modelo.estadisticas.IdentidadEstadisticaJugador(
                                                    99101L, "Service Atomic Player", "Forward", "Argentina",
                                                    88101L, "Service Atomic FC"),
                                            com.devchesterunited.devchesterunitedv2.modelo.estadisticas.FamiliaEstadistica.GENERAL,
                                            new com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador(
                                                    101, 2, 2, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                                                    BigDecimal.ONE, BigDecimal.valueOf(8), 0, 0)),
                                    new com.devchesterunited.devchesterunitedv2.modelo.estadisticas.RegistroEstadisticasJugador(
                                            new com.devchesterunited.devchesterunitedv2.modelo.estadisticas.IdentidadEstadisticaJugador(
                                                    99101L, "Service Atomic Player", "Forward", "Argentina",
                                                    88101L, "Service Atomic FC"),
                                            com.devchesterunited.devchesterunitedv2.modelo.estadisticas.FamiliaEstadistica.OFENSIVA,
                                            new com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador(
                                                    null, null, null, null, BigDecimal.ONE, BigDecimal.ONE, null, null, null, null)),
                                    new com.devchesterunited.devchesterunitedv2.modelo.estadisticas.RegistroEstadisticasJugador(
                                            new com.devchesterunited.devchesterunitedv2.modelo.estadisticas.IdentidadEstadisticaJugador(
                                                    99101L, "Service Atomic Player", "Forward", "Argentina",
                                                    88101L, "Service Atomic FC"),
                                            com.devchesterunited.devchesterunitedv2.modelo.estadisticas.FamiliaEstadistica.DEFENSIVA,
                                            new com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador(
                                                    null, null, null, null, null, null, BigDecimal.ONE, null, null, null))));
                }).toList()).when(proveedor).obtenerSnapshots(any());
        doThrow(new IllegalStateException("fallo controlado durante promoción")).when(estadisticas)
                .save(any(EstadisticasJugadorEntity.class));

        assertThatThrownBy(servicio::sincronizar).isInstanceOf(RuntimeException.class);

        assertThat(estadisticas.findByJugadorId(jugador.getId()).orElseThrow().getSyncId()).isEqualTo(anterior);
        assertThat(estadisticas.findByJugadorId(jugador.getId()).orElseThrow().getMinutosJugados()).isEqualTo(77);
        assertThat(identidadesEquipos.findByProveedorAndExternalId(
                com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna.WHOSCORED, 88101L))
                .isPresent();
        assertThat(identidadesJugadores.findByProveedorAndExternalId(
                com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna.WHOSCORED, 99101L))
                .isEmpty();
        var fallida = ejecuciones.findAllByEstado(EstadoEstadisticasSync.FALLIDA).stream()
                .filter(e -> !fallidasAntes.contains(e.getId())).findFirst().orElseThrow();
        assertThat(estadisticas.findAll()).noneMatch(e -> e.getSyncId().equals(fallida.getId()));
        fixtureSyncs.add(fallida.getId());
    }

    private EstadisticasSyncStagingEntity.Aplicacion aplicacion(UUID jugadorId, UUID equipoId, String familia) {
        return EstadisticasSyncStagingEntity.Aplicacion.builder()
                .jugadorId(jugadorId).equipoId(equipoId).teamId(88001L)
                .nombreEquipo("Atomic FC").posicion("Forward").nacionalidad("Argentina")
                .familia(familia);
    }
}
