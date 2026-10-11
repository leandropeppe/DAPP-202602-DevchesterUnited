package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.adapter.ProveedorEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.SnapshotEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.service.exception.EstadisticasProveedorException;
import com.devchesterunited.devchesterunitedv2.service.exception.IdentidadEstadisticaNoResueltaException;
import com.devchesterunited.devchesterunitedv2.service.exception.SincronizacionEstadisticasEnCursoException;
import com.devchesterunited.devchesterunitedv2.service.exception.SincronizacionEstadisticasException;
import com.devchesterunited.devchesterunitedv2.service.exception.SincronizacionPersistenciaException;
import com.devchesterunited.devchesterunitedv2.service.interfaces.SincronizacionEstadisticasJugadorService;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Orquesta red, staging y promoción sin compartir una transacción con el Adapter. */
@Service
public class SincronizacionEstadisticasJugadorServiceImpl
        implements SincronizacionEstadisticasJugadorService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SincronizacionEstadisticasJugadorServiceImpl.class);
    private final ProveedorEstadisticasJugador proveedor;
    private final IdentidadJugadorResolver jugadores;
    private final EstadisticasStagingService staging;
    private final EstadisticasPromocionService promocion;
    private final EstadisticasSyncLock lock;
    private final EstadisticasSyncService ciclo;

    public SincronizacionEstadisticasJugadorServiceImpl(ProveedorEstadisticasJugador proveedor,
            IdentidadJugadorResolver jugadores,
            EstadisticasStagingService staging, EstadisticasPromocionService promocion,
            EstadisticasSyncLock lock, EstadisticasSyncService ciclo) {
        this.proveedor = proveedor;
        this.jugadores = jugadores;
        this.staging = staging;
        this.promocion = promocion;
        this.lock = lock;
        this.ciclo = ciclo;
    }

    @Override
    public void sincronizar() {
        UUID syncId = null;
        boolean publicada = false;
        RuntimeException failure = null;
        var handle = lock.tryAcquire().orElseThrow(SincronizacionEstadisticasEnCursoException::new);
        try {
            syncId = ciclo.iniciar();
            ciclo.marcarRecibiendo(syncId);
            Map<Long, IdentidadJugadorResolver.NuevaIdentidad> nuevasJugadores = new LinkedHashMap<>();
            Map<Long, IdentidadEquipoResolver.NuevaIdentidad> nuevosEquipos = new LinkedHashMap<>();

            List<SnapshotEstadisticasJugador> snapshots = validarAdquisicionCompleta(
                    proveedor.obtenerSnapshots(List.of(CompeticionEstadistica.values())));
            Set<String> competicionesEsperadas = EnumSet.allOf(CompeticionEstadistica.class).stream()
                    .map(CompeticionEstadistica::name)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            for (var snapshot : snapshots) {
                procesarSnapshot(syncId, snapshot, nuevasJugadores, nuevosEquipos);
            }

            ciclo.marcarValidada(syncId);
            staging.validarCompeticionesCompletas(syncId, competicionesEsperadas);
            promocion.promover(syncId, nuevasJugadores.values(), nuevosEquipos.values(), Map.of());
            publicada = true;
        } catch (RuntimeException exception) {
            RuntimeException applicationException = traducir(exception);
            if (syncId != null && !publicada) {
                try {
                    ciclo.marcarFallida(syncId, applicationException);
                } catch (RuntimeException markingFailure) {
                    applicationException.addSuppressed(markingFailure);
                }
            }
            failure = applicationException;
        } finally {
            try {
                handle.close();
            } catch (RuntimeException lockFailure) {
                if (failure != null) {
                    failure.addSuppressed(lockFailure);
                } else if (publicada) {
                    LOGGER.warn("La sincronización fue publicada, pero falló la liberación del lock estadístico.",
                            lockFailure);
                } else {
                    failure = lockFailure;
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private RuntimeException traducir(RuntimeException exception) {
        if (exception instanceof IdentidadEstadisticaNoResueltaException
                || exception instanceof SincronizacionEstadisticasEnCursoException
                || exception instanceof EstadisticasProveedorException
                || exception instanceof SincronizacionPersistenciaException
                || exception instanceof SincronizacionEstadisticasException) {
            return exception;
        }
        if (exception instanceof org.springframework.dao.DataAccessException) {
            return new SincronizacionPersistenciaException(exception);
        }
        return new SincronizacionEstadisticasException(
                "No fue posible completar la sincronización estadística.", exception);
    }

    private List<SnapshotEstadisticasJugador> validarAdquisicionCompleta(
            List<SnapshotEstadisticasJugador> snapshots) {
        Set<CompeticionEstadistica> requeridas = EnumSet.allOf(CompeticionEstadistica.class);
        if (snapshots == null || snapshots.size() != requeridas.size()) {
            throw new EstadisticasProveedorException(
                    "El proveedor no entregó las cinco competiciones requeridas.");
        }
        Set<CompeticionEstadistica> recibidas = EnumSet.noneOf(CompeticionEstadistica.class);
        for (var snapshot : snapshots) {
            if (snapshot == null || snapshot.competicion() == null
                    || !requeridas.contains(snapshot.competicion())
                    || !recibidas.add(snapshot.competicion())) {
                throw new EstadisticasProveedorException(
                        "El proveedor devolvió competiciones faltantes o duplicadas.");
            }
        }
        if (!recibidas.equals(requeridas)) {
            throw new EstadisticasProveedorException(
                    "El proveedor no devolvió exactamente las cinco competiciones requeridas.");
        }
        return List.copyOf(snapshots);
    }

    private void procesarSnapshot(UUID syncId, SnapshotEstadisticasJugador snapshot,
            Map<Long, IdentidadJugadorResolver.NuevaIdentidad> nuevasJugadores,
            Map<Long, IdentidadEquipoResolver.NuevaIdentidad> nuevosEquipos) {
        if (snapshot.competicion() == null) {
            throw new IllegalStateException("El snapshot no tiene competición.");
        }
        var lote = new ArrayList<EstadisticasStagingService.Entrada>();
        for (var registro : snapshot.registros()) {
            var identidad = jugadores.resolver(registro.identidad(), snapshot.competicion());
            if (identidad.identidadNueva() != null) {
                nuevasJugadores.putIfAbsent(registro.identidad().playerId(), identidad.identidadNueva());
            }
            if (identidad.equipoNueva() != null) {
                nuevosEquipos.putIfAbsent(identidad.equipoNueva().externalId(), identidad.equipoNueva());
            }
            lote.add(new EstadisticasStagingService.Entrada(
                    identidad.jugadorId(), identidad.equipoId(), registro));
        }
        staging.escribirLote(syncId, snapshot.competicion().name(), lote);
    }
}
