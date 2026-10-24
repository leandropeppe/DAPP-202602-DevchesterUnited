package com.devchesterunited.devchesterunitedv2.exception;

/** Indica que una cantidad de tokens no es un entero positivo. */
public class CantidadTokenInvalidaException extends RuntimeException {

    public CantidadTokenInvalidaException(long cantidad) {
        super("La cantidad de tokens debe ser un entero positivo: " + cantidad);
    }
}
