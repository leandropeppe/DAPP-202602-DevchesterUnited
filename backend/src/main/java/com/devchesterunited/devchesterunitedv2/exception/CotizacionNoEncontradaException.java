package com.devchesterunited.devchesterunitedv2.exception;

public class CotizacionNoEncontradaException extends RuntimeException {
    public CotizacionNoEncontradaException() {
        super("No existe una cotización para el jugador y la estrategia solicitados.");
    }
}
