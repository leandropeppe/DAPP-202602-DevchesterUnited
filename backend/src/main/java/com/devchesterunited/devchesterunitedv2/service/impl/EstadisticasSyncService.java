package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.modelo.EstadoEstadisticasSync;
import com.devchesterunited.devchesterunitedv2.persistence.EstadisticasSyncRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EstadisticasSyncEntity;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

/** Administra el ciclo de vida de una ejecución sin exponer detalles del proveedor. */
@Service
/**
 * Cada transición técnica se confirma en una transacción independiente y corta.
 * El orquestador no mantiene una transacción durante HTTP ni staging; la única
 * transacción que incluye datos de negocio y PUBLICADA es la promoción atómica.
 */
public class EstadisticasSyncService {
    private final EstadisticasSyncRepository ejecuciones;
    private final Clock clock;

    public EstadisticasSyncService(EstadisticasSyncRepository ejecuciones, Clock clock) {
        this.ejecuciones = ejecuciones;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID iniciar() {
        UUID id = UUID.randomUUID();
        ejecuciones.save(new EstadisticasSyncEntity(id, EstadoEstadisticasSync.INICIADA,
                Instant.now(clock)));
        return id;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void marcarRecibiendo(UUID id) {
        cambiar(id, EstadoEstadisticasSync.RECIBIENDO, null, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void marcarValidada(UUID id) {
        cambiar(id, EstadoEstadisticasSync.VALIDADA, null, null);
    }

    /**
     * Intenta persistir FALLIDA en una transacción independiente y corta.
     * Si PostgreSQL está completamente indisponible, ninguna aplicación puede
     * garantizar que ese estado quede almacenado.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void marcarFallida(UUID id, Throwable causa) {
        cambiar(id, EstadoEstadisticasSync.FALLIDA, Instant.now(clock), sanitizar(causa));
    }

    private void cambiar(UUID id, EstadoEstadisticasSync estado, Instant finalizada, String error) {
        EstadisticasSyncEntity ejecucion = ejecuciones.findById(id)
                .orElseThrow(() -> new IllegalStateException("La ejecución estadística no existe."));
        ejecucion.cambiarEstado(estado, finalizada, error);
        ejecuciones.save(ejecucion);
    }

    static String sanitizar(Throwable causa) {
        if (causa == null) {
            return "Fallo de sincronización estadística.";
        }
        if (causa instanceof com.devchesterunited.devchesterunitedv2.service.exception.IdentidadEstadisticaNoResueltaException) {
            return "No fue posible resolver una identidad estadística.";
        }
        if (causa instanceof com.devchesterunited.devchesterunitedv2.service.exception.SincronizacionEstadisticasEnCursoException) {
            return "Ya hay una sincronización estadística en curso.";
        }
        if (causa instanceof org.springframework.dao.DataAccessException
                || causa instanceof com.devchesterunited.devchesterunitedv2.service.exception.SincronizacionPersistenciaException) {
            return "No fue posible persistir la sincronización estadística.";
        }
        return "No fue posible completar la sincronización estadística.";
    }
}
