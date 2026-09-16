package com.devchesterunited.devchesterunitedv2.service.exception;

public class SincronizacionEnCursoException extends RuntimeException {
    public SincronizacionEnCursoException() {
        super("Ya hay una sincronizacion del catalogo en curso");
    }
}
