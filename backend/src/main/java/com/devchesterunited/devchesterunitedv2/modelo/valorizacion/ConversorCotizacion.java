package com.devchesterunited.devchesterunitedv2.modelo.valorizacion;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ConversorCotizacion {
    private ConversorCotizacion() { }

    public static BigDecimal convertir(BigDecimal score, BigDecimal valorBase, BigDecimal factorEscala) {
        BigDecimal limitado = score.max(BigDecimal.ZERO).min(BigDecimal.ONE);
        return valorBase.add(limitado.multiply(factorEscala)).setScale(2, RoundingMode.HALF_UP);
    }
}
