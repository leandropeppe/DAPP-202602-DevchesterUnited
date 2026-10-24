package com.devchesterunited.devchesterunitedv2.modelo.market;

import com.devchesterunited.devchesterunitedv2.exception.CantidadTokenInvalidaException;

/** Cantidad entera positiva de tokens para una operación de mercado. */
public record TokenQuantity(long value) {

    public TokenQuantity {
        if (value <= 0) {
            throw new CantidadTokenInvalidaException(value);
        }
    }
}
