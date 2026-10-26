package com.devchesterunited.devchesterunitedv2.exception;

/** Violación de una invariante del dominio de jugador. */
public class JugadorException extends RuntimeException {
    public JugadorException(String mensaje) {
        super(mensaje);
    }
}
