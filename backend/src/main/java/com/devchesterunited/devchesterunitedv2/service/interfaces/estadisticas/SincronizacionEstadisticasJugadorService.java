package com.devchesterunited.devchesterunitedv2.service.interfaces.estadisticas;

import com.devchesterunited.devchesterunitedv2.service.dto.estadisticas.SincronizacionEstadisticasResultado;

/**
 * Caso de uso de sincronización de estadísticas de jugadores.
 *
 * La ejecución obtiene y valida staging antes de promover un snapshot completo;
 * el contrato no expone transporte, DTOs externos ni detalles de persistencia.
 */
public interface SincronizacionEstadisticasJugadorService {
    SincronizacionEstadisticasResultado sincronizar();
}
