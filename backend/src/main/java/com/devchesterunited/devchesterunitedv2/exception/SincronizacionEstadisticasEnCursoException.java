package com.devchesterunited.devchesterunitedv2.exception;

/** Impide dos sincronizaciones estadísticas concurrentes. */
public class SincronizacionEstadisticasEnCursoException extends RuntimeException {
    public SincronizacionEstadisticasEnCursoException() {
        super("Ya hay una sincronizacion estadistica en curso");
    }
}
