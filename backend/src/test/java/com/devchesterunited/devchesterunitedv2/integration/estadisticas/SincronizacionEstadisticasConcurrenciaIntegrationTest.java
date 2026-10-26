package com.devchesterunited.devchesterunitedv2.integration.estadisticas;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.EstadoEstadisticasSync;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.IdentidadEstadisticaJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.RegistroEstadisticasJugador;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.devchesterunited.devchesterunitedv2.exception.SincronizacionEstadisticasEnCursoException;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.IdentidadExternaEquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.entity.EstadisticasJugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.repository.EstadisticasJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.repository.EstadisticasSyncRepository;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.repository.EstadisticasSyncStagingRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.IdentidadExternaJugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.IdentidadExternaJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.scraping.estadisticas.ProveedorEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.service.interfaces.estadisticas.SincronizacionEstadisticasJugadorService;
import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@ActiveProfiles("test")
class SincronizacionEstadisticasConcurrenciaIntegrationTest extends PostgresTestSupport {
    @MockitoBean(answers = Answers.CALLS_REAL_METHODS) private ProveedorEstadisticasJugador proveedor;
    @Autowired private SincronizacionEstadisticasJugadorService servicio;
    @Autowired private EquipoRepository equipos;
    @Autowired private JugadorRepository jugadores;
    @Autowired private IdentidadExternaEquipoRepository identidadesEquipos;
    @Autowired private IdentidadExternaJugadorRepository identidadesJugadores;
    @Autowired private EstadisticasJugadorRepository estadisticas;
    @Autowired private EstadisticasSyncRepository ejecuciones;
    @Autowired private EstadisticasSyncStagingRepository staging;
    private UUID equipoId;
    private UUID jugadorId;
    private final java.util.Set<UUID> syncIds = new java.util.HashSet<>();
    private final java.util.Set<UUID> existingSyncIds = new java.util.HashSet<>();

    @AfterEach
    void limpiar() {
        syncIds.forEach(id -> staging.findAllBySyncId(id).forEach(staging::delete));
        if (jugadorId != null) {
            estadisticas.findByJugadorId(jugadorId).ifPresent(estadisticas::delete);
            identidadesJugadores.findByProveedorAndExternalId(ProveedorIdentidadExterna.WHOSCORED, 99001L)
                    .ifPresent(identidadesJugadores::delete);
        }
        syncIds.forEach(ejecuciones::deleteById);
        if (jugadorId != null) {
            jugadores.deleteById(jugadorId);
        }
        if (equipoId != null) {
            identidadesEquipos.findByProveedorAndExternalId(ProveedorIdentidadExterna.WHOSCORED, 88001L)
                    .ifPresent(identidadesEquipos::delete);
            equipos.deleteById(equipoId);
        }
        syncIds.clear();
        existingSyncIds.clear();
    }

    @Test
    void dosSincronizacionesConcurrentesSoloPermitenUnaPublicacion() throws Exception {
        prepararFixture();
        existingSyncIds.addAll(ejecuciones.findAll().stream().map(e -> e.getId()).toList());
        long publicacionesAntes = ejecuciones.findAllByEstado(EstadoEstadisticasSync.PUBLICADA).size();
        long identidadesAntes = identidadesJugadores.findAllByProveedor(ProveedorIdentidadExterna.WHOSCORED).size();
        long syncsAntes = ejecuciones.count();
        var primeraPaginaRecibida = new CountDownLatch(1);
        var liberarPrimera = new CountDownLatch(1);
        var primeraLlamada = new AtomicBoolean();
        doAnswer(invocacion -> {
                    if (primeraLlamada.compareAndSet(false, true)) {
                        primeraPaginaRecibida.countDown();
                        if (!liberarPrimera.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("La prueba no liberó la primera sincronización.");
                        }
                    }
                    return java.util.Arrays.stream(CompeticionEstadistica.values())
                            .map(this::snapshot).toList();
                }).when(proveedor).obtenerSnapshots(any());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var primera = executor.submit(servicio::sincronizar);
            assertThat(primeraPaginaRecibida.await(10, TimeUnit.SECONDS)).isTrue();
            var segunda = executor.submit(servicio::sincronizar);
            ExecutionException exception = assertThrows(ExecutionException.class,
                    () -> segunda.get(10, TimeUnit.SECONDS));
            assertThat(exception.getCause()).isInstanceOf(SincronizacionEstadisticasEnCursoException.class);
            assertThat(ejecuciones.count()).isEqualTo(syncsAntes + 1);
            assertThat(staging.findAll()).isEmpty();
            liberarPrimera.countDown();
            primera.get(20, TimeUnit.SECONDS);
        } finally {
            liberarPrimera.countDown();
            executor.shutdownNow();
        }

        assertThat(ejecuciones.findAllByEstado(EstadoEstadisticasSync.PUBLICADA)).hasSize((int) publicacionesAntes + 1);
        assertThat(identidadesJugadores.findAllByProveedor(ProveedorIdentidadExterna.WHOSCORED)).hasSize((int) identidadesAntes);
        assertThat(estadisticas.findByJugadorId(jugadorId)).isPresent();

        servicio.sincronizar();
        assertThat(ejecuciones.findAllByEstado(EstadoEstadisticasSync.PUBLICADA)).hasSize((int) publicacionesAntes + 2);
        assertThat(identidadesJugadores.findAllByProveedor(ProveedorIdentidadExterna.WHOSCORED)).hasSize((int) identidadesAntes);
        ejecuciones.findAll().stream().map(e -> e.getId())
                .filter(id -> !existingSyncIds.contains(id)).forEach(syncIds::add);
    }

    private void prepararFixture() {
        Instant now = Instant.now();
        var equipo = equipos.save(EquipoEntity.builder().id(UUID.randomUUID()).nombre("Concurrent FC")
                .liga("Premier League").activo(true).creadoEn(now).actualizadoEn(now).build());
        equipoId = equipo.getId();
        var jugador = jugadores.save(new JugadorEntity(UUID.randomUUID(), "Concurrent Player", equipoId,
                "Forward", 9, "Argentina", true));
        jugadorId = jugador.getId();
        identidadesEquipos.save(new IdentidadExternaEquipoEntity(UUID.randomUUID(), equipo,
                ProveedorIdentidadExterna.WHOSCORED, 88001L));
        identidadesJugadores.save(new IdentidadExternaJugadorEntity(UUID.randomUUID(), jugador,
                ProveedorIdentidadExterna.WHOSCORED, 99001L));
        UUID baseline = UUID.randomUUID();
        syncIds.add(baseline);
        ejecuciones.save(new com.devchesterunited.devchesterunitedv2.persistence.estadisticas.entity.EstadisticasSyncEntity(
                baseline, EstadoEstadisticasSync.PUBLICADA, now));
        estadisticas.save(new EstadisticasJugadorEntity(EstadisticasJugadorEntity.Creacion.builder()
                .id(UUID.randomUUID()).jugadorId(jugadorId).posicion("Forward").minutosJugados(80)
                .goles(1).asistencias(1).tiros(BigDecimal.ONE).pasesClave(BigDecimal.ONE)
                .regates(BigDecimal.ONE).tackles(BigDecimal.ONE).valoracion(BigDecimal.valueOf(6))
                .tarjetasAmarillas(0).tarjetasRojas(0).syncId(baseline).creadoEn(now).actualizadoEn(now)));
    }

    private com.devchesterunited.devchesterunitedv2.modelo.estadisticas.SnapshotEstadisticasJugador snapshot(
            CompeticionEstadistica competicion) {
        var registro = new RegistroEstadisticasJugador(
                new IdentidadEstadisticaJugador(99001L, "Concurrent Player", "Forward", "Argentina",
                        88001L, "Concurrent FC"),
                com.devchesterunited.devchesterunitedv2.modelo.estadisticas.FamiliaEstadistica.GENERAL,
                new MetricasEstadisticasJugador(100, 2, 2, BigDecimal.ONE, BigDecimal.ONE,
                        BigDecimal.ONE, BigDecimal.ONE, BigDecimal.valueOf(7), 0, 0));
        return new com.devchesterunited.devchesterunitedv2.modelo.estadisticas.SnapshotEstadisticasJugador(
                competicion, java.util.List.of(registro));
    }
}
