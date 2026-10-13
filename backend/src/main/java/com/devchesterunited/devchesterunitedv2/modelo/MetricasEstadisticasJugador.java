package com.devchesterunited.devchesterunitedv2.modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;

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
        tiros = normalizarDecimal(tiros);
        pasesClave = normalizarDecimal(pasesClave);
        regates = normalizarDecimal(regates);
        tackles = normalizarDecimal(tackles);
        valoracion = normalizarDecimal(valoracion);
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

    private static BigDecimal normalizarDecimal(BigDecimal valor) {
        return valor == null ? null : valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static void validarNoNegativo(Number valor, String campo) {
        if (valor instanceof BigDecimal decimal && decimal.signum() < 0
                || valor instanceof Integer entero && entero < 0) {
            throw new IllegalArgumentException("La métrica " + campo + " no puede ser negativa.");
        }
    }
}
