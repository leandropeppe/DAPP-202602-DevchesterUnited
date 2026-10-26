package com.devchesterunited.devchesterunitedv2.exception;

public class JugadorNoEncontradoException extends RuntimeException {
    public JugadorNoEncontradoException() {
        super("El jugador no existe.");
    }
}
