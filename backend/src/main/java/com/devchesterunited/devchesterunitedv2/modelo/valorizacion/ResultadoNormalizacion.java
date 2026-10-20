package com.devchesterunited.devchesterunitedv2.modelo.valorizacion;

import java.math.BigDecimal;

public record ResultadoNormalizacion(MetricaValorizacion metrica, BigDecimal valorBruto,
        BigDecimal minimo, BigDecimal maximo, BigDecimal valorNormalizado,
        String alcance, String posicionReferencia, Estado estado) {
    public enum Estado { CALCULADO, CONSTANTE, AUSENTE }
}
