package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.adapter.ProveedorEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.FamiliaEstadistica;
import com.devchesterunited.devchesterunitedv2.service.exception.SincronizacionEstadisticasEnCursoException;
import com.devchesterunited.devchesterunitedv2.service.exception.IdentidadEstadisticaNoResueltaException;
import com.devchesterunited.devchesterunitedv2.service.exception.EstadisticasProveedorException;
import com.devchesterunited.devchesterunitedv2.service.exception.SincronizacionPersistenciaException;
import com.devchesterunited.devchesterunitedv2.service.exception.SincronizacionEstadisticasException;
import com.devchesterunited.devchesterunitedv2.service.interfaces.SincronizacionEstadisticasJugadorService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final int tamanoLote;

    public SincronizacionEstadisticasJugadorServiceImpl(ProveedorEstadisticasJugador proveedor,
            IdentidadJugadorResolver jugadores,
            EstadisticasStagingService staging, EstadisticasPromocionService promocion,
            EstadisticasSyncLock lock, @Value("${estadisticas.page-size:50}") int tamanoLote,
            EstadisticasSyncService ciclo) {
        this.proveedor = proveedor;
        this.jugadores = jugadores;
        this.staging = staging;
        this.promocion = promocion;
        this.lock = lock;
        this.tamanoLote = tamanoLote;
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
            Set<String> combinacionesCompletas = new HashSet<>();
            for (CompeticionEstadistica competicion : CompeticionEstadistica.values()) {
                for (FamiliaEstadistica familia : FamiliaEstadistica.values()) {
                    procesarFamilia(syncId, competicion, familia, nuevasJugadores, nuevosEquipos,
                            combinacionesCompletas);
                }
            }
            ciclo.marcarValidada(syncId);
            staging.validarCompleto(syncId, combinacionesCompletas);
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

    private void procesarFamilia(UUID syncId, CompeticionEstadistica competicion,
            FamiliaEstadistica familia, Map<Long, IdentidadJugadorResolver.NuevaIdentidad> nuevasJugadores,
            Map<Long, IdentidadEquipoResolver.NuevaIdentidad> nuevosEquipos,
            Set<String> combinacionesCompletas) {
        int pagina = 1;
        Integer totalPaginas = null;
        Integer totalResultados = null;
        Integer tamanoPagina = null;
        int recibidos = 0;
        Set<Integer> paginasRecibidas = new HashSet<>();
        do {
            var page = obtenerPagina(competicion, familia, pagina);
            if (page.competicion() != competicion || page.familia() != familia
                    || page.paginaActual() != pagina || !paginasRecibidas.add(page.paginaActual())) {
                throw new IllegalStateException("La fuente devolvió una página inconsistente.");
            }
            if (totalPaginas == null) {
                totalPaginas = page.totalPaginas();
                totalResultados = page.totalResultados();
                tamanoPagina = page.tamanoPagina();
            } else if (totalPaginas != page.totalPaginas()
                    || totalResultados != page.totalResultados()
                    || tamanoPagina != page.tamanoPagina()) {
                throw new IllegalStateException("La metadata de paginación de la fuente es inconsistente.");
            }
            recibidos += page.registros().size();
            var lote = new ArrayList<EstadisticasStagingService.Entrada>();
            for (var registro : page.registros()) {
                var identidad = jugadores.resolver(registro.identidad(), competicion);
                if (identidad.identidadNueva() != null) {
                    nuevasJugadores.putIfAbsent(registro.identidad().playerId(), identidad.identidadNueva());
                }
                if (identidad.equipoNueva() != null) {
                    nuevosEquipos.putIfAbsent(identidad.equipoNueva().externalId(), identidad.equipoNueva());
                }
                lote.add(new EstadisticasStagingService.Entrada(
                        identidad.jugadorId(), identidad.equipoId(), registro));
            }
            staging.escribirLote(syncId, competicion.name(), lote);
            pagina++;
        } while (pagina <= totalPaginas);
        if (paginasRecibidas.size() != totalPaginas || recibidos != totalResultados) {
            throw new IllegalStateException("La fuente no entregó todos los registros esperados.");
        }
        combinacionesCompletas.add(competicion.name() + ":" + familia.name());
    }

    private com.devchesterunited.devchesterunitedv2.modelo.PaginaEstadisticasJugador obtenerPagina(
            CompeticionEstadistica competicion, FamiliaEstadistica familia, int pagina) {
        try {
            return proveedor.obtenerPagina(competicion, familia, pagina, tamanoLote);
        } catch (RuntimeException exception) {
            throw new EstadisticasProveedorException(
                    "El proveedor estadístico no entregó una respuesta válida.");
        }
    }
}
