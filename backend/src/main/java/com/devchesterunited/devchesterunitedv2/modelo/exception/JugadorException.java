package com.devchesterunited.devchesterunitedv2.modelo.exception;

/** ViolaciÃ³n de una invariante del dominio de jugador. */
public class JugadorException extends RuntimeException {
    public JugadorException(String mensaje) {
        super(mensaje);
    }
}
