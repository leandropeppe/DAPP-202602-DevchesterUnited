package com.devchesterunited.devchesterunitedv2.service.exception;

public class JugadorNoEncontradoException extends RuntimeException {
    public JugadorNoEncontradoException() {
        super("El jugador no existe.");
    }
}
