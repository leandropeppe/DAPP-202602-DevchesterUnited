package com.devchesterunited.devchesterunitedv2.exception;

/**
 * Error público de una sincronización fallida.
 * El cause solo se conserva para diagnóstico interno; nunca se persiste ni se serializa.
 */
public class SincronizacionEstadisticasException extends RuntimeException {
    public SincronizacionEstadisticasException(String message, Throwable cause) {
        super(message, cause);
    }
}
