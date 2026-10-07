package com.devchesterunited.devchesterunitedv2.service.exception;

/** Indica que una identidad externa no tiene una asociación local inequívoca. */
public class IdentidadEstadisticaNoResueltaException extends RuntimeException {
    public IdentidadEstadisticaNoResueltaException(String message) {
        super(message);
    }
}
