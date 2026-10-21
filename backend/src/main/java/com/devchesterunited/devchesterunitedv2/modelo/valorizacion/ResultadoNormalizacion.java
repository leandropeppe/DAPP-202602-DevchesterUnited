package com.devchesterunited.devchesterunitedv2.modelo.valorizacion;

import java.math.BigDecimal;

public record ResultadoNormalizacion(MetricaValorizacion metrica, BigDecimal valorBruto,
        BigDecimal minimo, BigDecimal maximo, BigDecimal valorNormalizado,
        String alcance, String posicionReferencia, Estado estado, String metodo) {
    public ResultadoNormalizacion(MetricaValorizacion metrica, BigDecimal valorBruto,
            BigDecimal minimo, BigDecimal maximo, BigDecimal valorNormalizado,
            String alcance, String posicionReferencia, Estado estado) {
        this(metrica, valorBruto, minimo, maximo, valorNormalizado, alcance, posicionReferencia, estado, "MIN_MAX");
    }

    public enum Estado { CALCULADO, CONSTANTE, AUSENTE }
}
