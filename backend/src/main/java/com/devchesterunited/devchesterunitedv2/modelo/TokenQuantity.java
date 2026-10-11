package com.devchesterunited.devchesterunitedv2.modelo;

import com.devchesterunited.devchesterunitedv2.modelo.exception.CantidadTokenInvalidaException;

/** Cantidad entera positiva de tokens para una operación de mercado. */
public record TokenQuantity(long value) {

    public TokenQuantity {
        if (value <= 0) {
            throw new CantidadTokenInvalidaException(value);
        }
    }
}
