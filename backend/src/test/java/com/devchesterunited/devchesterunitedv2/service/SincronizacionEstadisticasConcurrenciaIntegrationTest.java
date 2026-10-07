package com.devchesterunited.devchesterunitedv2.service;

import com.devchesterunited.devchesterunitedv2.adapter.ProveedorEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.EstadoEstadisticasSync;
import com.devchesterunited.devchesterunitedv2.modelo.FamiliaEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.IdentidadEstadisticaJugador;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.PaginaEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.modelo.RegistroEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.persistence.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.EstadisticasJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.EstadisticasSyncRepository;
import com.devchesterunited.devchesterunitedv2.persistence.EstadisticasSyncStagingRepository;
import com.devchesterunited.devchesterunitedv2.persistence.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.IdentidadExternaJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EstadisticasJugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.IdentidadExternaEquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.IdentidadExternaJugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.service.exception.SincronizacionEstadisticasEnCursoException;
import com.devchesterunited.devchesterunitedv2.service.interfaces.SincronizacionEstadisticasJugadorService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class SincronizacionEstadisticasConcurrenciaIntegrationTest extends PostgresTestSupport {
    @MockBean private ProveedorEstadisticasJugador proveedor;
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
        when(proveedor.obtenerPagina(any(), any(), any(Integer.class), any(Integer.class)))
                .thenAnswer(invocacion -> {
                    if (primeraLlamada.compareAndSet(false, true)) {
                        primeraPaginaRecibida.countDown();
                        if (!liberarPrimera.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("La prueba no liberó la primera sincronización.");
                        }
                    }
                    CompeticionEstadistica competicion = invocacion.getArgument(0);
                    FamiliaEstadistica familia = invocacion.getArgument(1);
                    return pagina(competicion, familia);
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var primera = executor.submit(servicio::sincronizar);
            assertThat(primeraPaginaRecibida.await(10, TimeUnit.SECONDS)).isTrue();
            var segunda = executor.submit(servicio::sincronizar);
            try {
                segunda.get(10, TimeUnit.SECONDS);
                fail("La segunda sincronización debió ser rechazada por el lock");
            } catch (ExecutionException exception) {
                assertThat(exception.getCause()).isInstanceOf(SincronizacionEstadisticasEnCursoException.class);
            }
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
        ejecuciones.save(new com.devchesterunited.devchesterunitedv2.persistence.dto.EstadisticasSyncEntity(
                baseline, EstadoEstadisticasSync.PUBLICADA, now));
        estadisticas.save(new EstadisticasJugadorEntity(UUID.randomUUID(), jugadorId, "Forward", 80,
                1, 1, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.valueOf(6), 0, 0, baseline, now, now));
    }

    private PaginaEstadisticasJugador pagina(CompeticionEstadistica competicion,
            FamiliaEstadistica familia) {
        var registro = new RegistroEstadisticasJugador(
                new IdentidadEstadisticaJugador(99001L, "Concurrent Player", "Forward", "Argentina",
                        88001L, "Concurrent FC"), familia,
                new MetricasEstadisticasJugador(100, 2, 2, BigDecimal.ONE, BigDecimal.ONE,
                        BigDecimal.ONE, BigDecimal.ONE, BigDecimal.valueOf(7), 0, 0));
        return new PaginaEstadisticasJugador(competicion, familia, 1, 50, 1, 1,
                java.util.List.of(registro));
    }
}
