package com.devchesterunited.devchesterunitedv2.modelo;

import java.math.BigDecimal;

/** Métricas normalizadas de una familia estadística. Los null representan ausencia legítima. */
public record MetricasEstadisticasJugador(
        Integer minutosJugados,
        Integer goles,
        Integer asistencias,
        BigDecimal tiros,
        BigDecimal pasesClave,
        BigDecimal regates,
        BigDecimal tackles,
        BigDecimal valoracion,
        Integer tarjetasAmarillas,
        Integer tarjetasRojas) {

    public MetricasEstadisticasJugador {
        validarNoNegativo(minutosJugados, "minutosJugados");
        validarNoNegativo(goles, "goles");
        validarNoNegativo(asistencias, "asistencias");
        validarNoNegativo(tiros, "tiros");
        validarNoNegativo(pasesClave, "pasesClave");
        validarNoNegativo(regates, "regates");
        validarNoNegativo(tackles, "tackles");
        validarNoNegativo(valoracion, "valoracion");
        validarNoNegativo(tarjetasAmarillas, "tarjetasAmarillas");
        validarNoNegativo(tarjetasRojas, "tarjetasRojas");
    }

    private static void validarNoNegativo(Number valor, String campo) {
        if (valor instanceof BigDecimal decimal && decimal.signum() < 0
                || valor instanceof Integer entero && entero < 0) {
            throw new IllegalArgumentException("La métrica " + campo + " no puede ser negativa.");
        }
    }
}
