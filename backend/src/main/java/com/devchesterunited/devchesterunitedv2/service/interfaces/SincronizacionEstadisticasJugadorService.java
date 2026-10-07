package com.devchesterunited.devchesterunitedv2.service.interfaces;

/**
 * Caso de uso de sincronización de estadísticas de jugadores.
 *
 * La ejecución obtiene y valida staging antes de promover un snapshot completo;
 * el contrato no expone transporte, DTOs externos ni detalles de persistencia.
 */
public interface SincronizacionEstadisticasJugadorService {
    void sincronizar();
}
