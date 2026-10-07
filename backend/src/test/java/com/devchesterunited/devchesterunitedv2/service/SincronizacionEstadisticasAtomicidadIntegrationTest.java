package com.devchesterunited.devchesterunitedv2.service;

import com.devchesterunited.devchesterunitedv2.adapter.ProveedorEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.adapter.WhoScoredStatisticsException;
import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.modelo.EstadoEstadisticasSync;
import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.FamiliaEstadistica;
import com.devchesterunited.devchesterunitedv2.persistence.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.EstadisticasJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.EstadisticasSyncRepository;
import com.devchesterunited.devchesterunitedv2.persistence.EstadisticasSyncStagingRepository;
import com.devchesterunited.devchesterunitedv2.persistence.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.IdentidadExternaJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EstadisticasJugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EstadisticasSyncEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EstadisticasSyncStagingEntity;
import com.devchesterunited.devchesterunitedv2.service.impl.EstadisticasPromocionService;
import com.devchesterunited.devchesterunitedv2.service.impl.IdentidadEquipoResolver;
import com.devchesterunited.devchesterunitedv2.service.impl.IdentidadJugadorResolver;
import com.devchesterunited.devchesterunitedv2.service.interfaces.SincronizacionEstadisticasJugadorService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
class SincronizacionEstadisticasAtomicidadIntegrationTest extends PostgresTestSupport {
    @MockBean private ProveedorEstadisticasJugador proveedor;
    @Autowired private SincronizacionEstadisticasJugadorService servicio;
    @Autowired private EquipoRepository equipos;
    @Autowired private JugadorRepository jugadores;
    @Autowired private EstadisticasSyncRepository ejecuciones;
    @SpyBean private EstadisticasJugadorRepository estadisticas;
    @Autowired private EstadisticasSyncStagingRepository staging;
    @Autowired private IdentidadExternaEquipoRepository identidadesEquipos;
    @Autowired private IdentidadExternaJugadorRepository identidadesJugadores;
    @Autowired private EstadisticasPromocionService promocion;
    private final java.util.Set<UUID> fixtureJugadores = new java.util.HashSet<>();
    private final java.util.Set<UUID> fixtureEquipos = new java.util.HashSet<>();
    private final java.util.Set<UUID> fixtureSyncs = new java.util.HashSet<>();

    @AfterEach
    void limpiarFixtures() {
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
        estadisticas.save(new EstadisticasJugadorEntity(UUID.randomUUID(), jugador.getId(), "Forward", 90,
                3, 1, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.valueOf(7), 0, 0, publicada, now, now));
        when(proveedor.obtenerPagina(any(), any(), any(Integer.class), any(Integer.class)))
                .thenThrow(new IllegalStateException("provider internal secret"));

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
        estadisticas.save(new EstadisticasJugadorEntity(UUID.randomUUID(), jugador.getId(), "Forward", 75,
                1, 1, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.valueOf(6), 0, 0, anterior, now, now));
        when(proveedor.obtenerPagina(any(), any(), any(Integer.class), any(Integer.class)))
                .thenThrow(new WhoScoredStatisticsException(
                        "https://whoscored.test/private?token=secret body=full-sensitive-body header=Bearer-secret",
                        new IllegalStateException("internal stack secret")));

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
        estadisticas.save(new EstadisticasJugadorEntity(UUID.randomUUID(), jugador.getId(), "Forward", 88,
                2, 1, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.valueOf(6), 0, 0, anterior, now, now));

        UUID syncId = UUID.randomUUID();
        fixtureSyncs.add(syncId);
        ejecuciones.save(new EstadisticasSyncEntity(syncId, EstadoEstadisticasSync.VALIDADA, now));
        var fila = new EstadisticasSyncStagingEntity(UUID.randomUUID(), syncId, "PREMIER_LEAGUE", 99001L,
                "Atomic Player");
        fila.aplicar(jugador.getId(), equipo.getId(), 88001L, "Atomic FC", "Forward", "Argentina",
                "GENERAL", 100, 4, 2, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.valueOf(8), 0, 0);
        fila.aplicar(jugador.getId(), equipo.getId(), 88001L, "Atomic FC", "Forward", "Argentina",
                "OFENSIVA", null, null, null, null, null, null, null, null, null, null);
        fila.aplicar(jugador.getId(), equipo.getId(), 88001L, "Atomic FC", "Forward", "Argentina",
                "DEFENSIVA", null, null, null, null, null, null, null, null, null, null);
        staging.save(fila);

        doThrow(new IllegalStateException("persistencia controlada")).when(estadisticas)
                .save(any(EstadisticasJugadorEntity.class));
        var nuevoEquipo = new IdentidadEquipoResolver.NuevaIdentidad(equipo.getId(), 88001L, equipo);
        var nuevoJugador = new IdentidadJugadorResolver.NuevaIdentidad(jugador.getId(), 99001L, jugador);

        assertThatThrownBy(() -> promocion.promover(syncId, java.util.List.of(nuevoJugador),
                java.util.List.of(nuevoEquipo), java.util.Map.of()))
                .isInstanceOf(RuntimeException.class);

        assertThat(estadisticas.findByJugadorId(jugador.getId()).orElseThrow().getSyncId()).isEqualTo(anterior);
        assertThat(identidadesEquipos.findByProveedorAndExternalId(
                com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna.WHOSCORED, 88001L))
                .isEmpty();
        assertThat(identidadesJugadores.findByProveedorAndExternalId(
                com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna.WHOSCORED, 99001L))
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
        var jugador = jugadores.save(new JugadorEntity(UUID.randomUUID(), "Service Atomic Player",
                equipo.getId(), "Forward", 9, "Argentina", true));
        fixtureJugadores.add(jugador.getId());
        UUID anterior = UUID.randomUUID();
        fixtureSyncs.add(anterior);
        ejecuciones.save(new EstadisticasSyncEntity(anterior, EstadoEstadisticasSync.PUBLICADA, now));
        estadisticas.save(new EstadisticasJugadorEntity(UUID.randomUUID(), jugador.getId(), "Forward", 77,
                1, 1, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.valueOf(6), 0, 0, anterior, now, now));
        when(proveedor.obtenerPagina(any(), any(), any(Integer.class), any(Integer.class)))
                .thenAnswer(invocacion -> {
                    CompeticionEstadistica competicion = invocacion.getArgument(0);
                    FamiliaEstadistica familia = invocacion.getArgument(1);
                    if (competicion != CompeticionEstadistica.PREMIER_LEAGUE) {
                        return new com.devchesterunited.devchesterunitedv2.modelo.PaginaEstadisticasJugador(
                                competicion, familia, 1, 50, 1, 0, java.util.List.of());
                    }
                    return new com.devchesterunited.devchesterunitedv2.modelo.PaginaEstadisticasJugador(
                            competicion, familia, 1, 50, 1, 1, java.util.List.of(
                                    new com.devchesterunited.devchesterunitedv2.modelo.RegistroEstadisticasJugador(
                                            new com.devchesterunited.devchesterunitedv2.modelo.IdentidadEstadisticaJugador(
                                                    99101L, "Service Atomic Player", "Forward", "Argentina",
                                                    88101L, "Service Atomic FC"), familia,
                                            new com.devchesterunited.devchesterunitedv2.modelo.MetricasEstadisticasJugador(
                                                    101, 2, 2, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                                                    BigDecimal.ONE, BigDecimal.valueOf(8), 0, 0))));
                });
        doThrow(new IllegalStateException("fallo controlado durante promoción")).when(estadisticas)
                .save(any(EstadisticasJugadorEntity.class));

        assertThatThrownBy(servicio::sincronizar).isInstanceOf(RuntimeException.class);

        assertThat(estadisticas.findByJugadorId(jugador.getId()).orElseThrow().getSyncId()).isEqualTo(anterior);
        assertThat(estadisticas.findByJugadorId(jugador.getId()).orElseThrow().getMinutosJugados()).isEqualTo(77);
        assertThat(identidadesEquipos.findByProveedorAndExternalId(
                com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna.WHOSCORED, 88101L))
                .isEmpty();
        assertThat(identidadesJugadores.findByProveedorAndExternalId(
                com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna.WHOSCORED, 99101L))
                .isEmpty();
        var fallida = ejecuciones.findAllByEstado(EstadoEstadisticasSync.FALLIDA).stream()
                .filter(e -> !fallidasAntes.contains(e.getId())).findFirst().orElseThrow();
        assertThat(staging.findAllBySyncId(fallida.getId())).isNotEmpty();
        assertThat(estadisticas.findAll()).noneMatch(e -> e.getSyncId().equals(fallida.getId()));
        fixtureSyncs.add(fallida.getId());
    }
}
