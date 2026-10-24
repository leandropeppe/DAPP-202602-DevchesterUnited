package com.devchesterunited.devchesterunitedv2.exception;

public class SincronizacionEnCursoException extends RuntimeException {
    public SincronizacionEnCursoException() {
        super("Ya hay una sincronizacion del catalogo en curso");
    }
}
