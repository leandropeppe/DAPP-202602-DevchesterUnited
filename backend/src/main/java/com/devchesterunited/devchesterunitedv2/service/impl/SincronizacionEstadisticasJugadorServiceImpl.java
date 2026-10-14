package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.adapter.ProveedorEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.SnapshotEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.service.exception.EstadisticasProveedorException;
import com.devchesterunited.devchesterunitedv2.service.exception.IdentidadEstadisticaNoResueltaException;
import com.devchesterunited.devchesterunitedv2.service.exception.SincronizacionEstadisticasEnCursoException;
import com.devchesterunited.devchesterunitedv2.service.exception.SincronizacionEstadisticasException;
import com.devchesterunited.devchesterunitedv2.service.exception.SincronizacionPersistenciaException;
import com.devchesterunited.devchesterunitedv2.service.dto.SincronizacionEstadisticasResultado;
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
    private final IdentidadEquipoResolver equipos;
    private final IdentidadJugadorResolver jugadores;
    private final EstadisticasStagingService staging;
    private final EstadisticasPromocionService promocion;
    private final EstadisticasSyncLock lock;
    private final EstadisticasSyncService ciclo;

    public SincronizacionEstadisticasJugadorServiceImpl(ProveedorEstadisticasJugador proveedor,
            IdentidadEquipoResolver equipos, IdentidadJugadorResolver jugadores,
            EstadisticasStagingService staging, EstadisticasPromocionService promocion,
            EstadisticasSyncLock lock, EstadisticasSyncService ciclo) {
        this.proveedor = proveedor;
        this.equipos = equipos;
        this.jugadores = jugadores;
        this.staging = staging;
        this.promocion = promocion;
        this.lock = lock;
        this.ciclo = ciclo;
    }

    @Override
    public SincronizacionEstadisticasResultado sincronizar() {
        UUID syncId = null;
        boolean publicada = false;
        RuntimeException failure = null;
        SincronizacionEstadisticasResultado resultado = null;
        var handle = lock.tryAcquire().orElseThrow(SincronizacionEstadisticasEnCursoException::new);
        try {
            syncId = ciclo.iniciar();
            ciclo.marcarRecibiendo(syncId);
            resultado = ejecutarSincronizacion(syncId);
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
        return resultado;
    }

    private SincronizacionEstadisticasResultado ejecutarSincronizacion(UUID syncId) {
        Map<Long, IdentidadJugadorResolver.NuevaIdentidad> nuevasJugadores = new LinkedHashMap<>();
        List<IdentidadEstadisticaNoResueltaException.Diagnostico> omitidos = new ArrayList<>();
        List<SnapshotEstadisticasJugador> snapshots = validarAdquisicionCompleta(
                proveedor.obtenerSnapshots(List.of(CompeticionEstadistica.values())));
        Map<Long, EquipoEntity> equiposPorWhoscoredTeamId = resolverEquipos(snapshots);
        Set<String> competicionesEsperadas = EnumSet.allOf(CompeticionEstadistica.class).stream()
                .map(CompeticionEstadistica::name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        int adquiridos = snapshots.stream().mapToInt(s -> s.registros().size()).sum();
        List<PendingSnapshot> pendientes = snapshots.stream()
                .map(snapshot -> prepararSnapshot(snapshot, nuevasJugadores,
                        equiposPorWhoscoredTeamId, omitidos))
                .toList();
        for (var pendiente : pendientes) {
            staging.escribirLote(syncId, pendiente.competicion(), pendiente.entradas());
        }
        ciclo.marcarValidada(syncId);
        staging.validarCompeticionesCompletas(syncId, competicionesEsperadas);
        promocion.promover(syncId, nuevasJugadores.values(), List.of(), Map.of());
        var resultado = new SincronizacionEstadisticasResultado(adquiridos, adquiridos - omitidos.size(),
                omitidos.size(), omitidos);
        if (!omitidos.isEmpty()) {
            LOGGER.warn("Sincroniz\u00f3n estad\u00edstica publicada: adquiridos={}, publicados={}, omitidos={}",
                    resultado.adquiridos(), resultado.publicados(), resultado.omitidos());
        }
        return resultado;
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

    private PendingSnapshot prepararSnapshot(SnapshotEstadisticasJugador snapshot,
            Map<Long, IdentidadJugadorResolver.NuevaIdentidad> nuevasJugadores,
            Map<Long, EquipoEntity> equiposPorWhoscoredTeamId,
            List<IdentidadEstadisticaNoResueltaException.Diagnostico> omitidos) {
        if (snapshot.competicion() == null) {
            throw new IllegalStateException("El snapshot no tiene competición.");
        }
        var lote = new ArrayList<EstadisticasStagingService.Entrada>();
        for (var registro : snapshot.registros()) {
            IdentidadJugadorResolver.Resultado identidad;
            try {
                EquipoEntity equipo = equiposPorWhoscoredTeamId.get(registro.identidad().teamId());
                if (equipo == null) {
                    throw new IllegalStateException("No hay equipo resuelto para el teamId del snapshot.");
                }
                identidad = jugadores.resolver(registro.identidad(), equipo, snapshot.competicion());
            } catch (IdentidadEstadisticaNoResueltaException exception) {
                omitidos.addAll(exception.diagnosticos());
                continue;
            }
            if (identidad.identidadNueva() != null) {
                nuevasJugadores.putIfAbsent(registro.identidad().playerId(), identidad.identidadNueva());
            }
            lote.add(new EstadisticasStagingService.Entrada(
                    identidad.jugadorId(), identidad.equipoId(), registro));
        }
        return new PendingSnapshot(snapshot.competicion().name(), lote);
    }

    private Map<Long, EquipoEntity> resolverEquipos(List<SnapshotEstadisticasJugador> snapshots) {
        Map<Long, EquipoHint> unicos = new LinkedHashMap<>();
        List<IdentidadEstadisticaNoResueltaException.Diagnostico> errores = new ArrayList<>();
        for (var snapshot : snapshots) {
            for (var registro : snapshot.registros()) {
                var hint = registro.identidad();
                if (hint.teamId() == null) {
                    errores.add(new IdentidadEstadisticaNoResueltaException.Diagnostico(
                            IdentidadEstadisticaNoResueltaException.Tipo.EQUIPO, "WHOSCORED", null,
                            hint.nombreEquipo(), snapshot.competicion().name(), hint.nombreEquipo(), null,
                            null, null, IdentidadEstadisticaNoResueltaException.Motivo.EVIDENCIA_INSUFICIENTE));
                } else {
                    unicos.putIfAbsent(hint.teamId(), new EquipoHint(hint.nombreEquipo(), snapshot.competicion()));
                }
            }
        }
        Map<Long, EquipoEntity> resultado = new LinkedHashMap<>();
        for (var entry : unicos.entrySet()) {
            try {
                resultado.put(entry.getKey(), equipos.resolver(entry.getKey(), entry.getValue().nombre(),
                        entry.getValue().competicion()).equipo());
            } catch (IdentidadEstadisticaNoResueltaException exception) {
                errores.addAll(exception.diagnosticos());
            }
        }
        if (!errores.isEmpty()) throw new IdentidadEstadisticaNoResueltaException(errores);
        return resultado;
    }

    private record EquipoHint(String nombre, CompeticionEstadistica competicion) { }

    private record PendingSnapshot(String competicion, List<EstadisticasStagingService.Entrada> entradas) { }
}
