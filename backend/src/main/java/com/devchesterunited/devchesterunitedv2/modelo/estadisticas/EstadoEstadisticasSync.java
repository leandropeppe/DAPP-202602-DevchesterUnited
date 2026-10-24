package com.devchesterunited.devchesterunitedv2.modelo.estadisticas;

/**
 * Estados técnicos de una ejecución de sincronización estadística.
 *
 * {@code RECIBIENDO} y {@code VALIDADA} describen trabajo aún no visible para
 * negocio; solo {@code PUBLICADA} hace vigente el snapshot. Un error desde
 * cualquier estado previo a publicación conduce a {@code FALLIDA}.
 */
public enum EstadoEstadisticasSync {
    INICIADA,
    RECIBIENDO,
    VALIDADA,
    PUBLICADA,
    FALLIDA
}
