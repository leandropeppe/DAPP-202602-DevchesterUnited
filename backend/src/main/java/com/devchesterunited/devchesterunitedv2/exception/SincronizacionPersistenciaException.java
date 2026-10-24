package com.devchesterunited.devchesterunitedv2.exception;

/** Error de persistencia durante una sincronizacion; no representa competencia de locks. */
public class SincronizacionPersistenciaException extends RuntimeException {
    public SincronizacionPersistenciaException(Throwable cause) {
        super(cause);
    }
}
