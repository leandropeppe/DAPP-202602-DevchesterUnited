package com.devchesterunited.devchesterunitedv2.modelo.valorizacion;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ResultadoValorizacion(UUID jugadorId, String estrategiaId, int version,
        BigDecimal score, BigDecimal valor, List<ResultadoNormalizacion> normalizaciones,
        List<MetricaValorizacion> penalizacionesAplicadas,
        ResultadoElegibilidad elegibilidad, BigDecimal scorePosicionalAntesAjuste,
        BigDecimal factorMercadoPosicion, String posicionNormalizadaFactor) {
    public ResultadoValorizacion(UUID jugadorId, String estrategiaId, int version,
            BigDecimal score, BigDecimal valor, List<ResultadoNormalizacion> normalizaciones,
            List<MetricaValorizacion> penalizacionesAplicadas, ResultadoElegibilidad elegibilidad) {
        this(jugadorId, estrategiaId, version, score, valor, normalizaciones, penalizacionesAplicadas,
                elegibilidad, null, null, null);
    }
    public ResultadoValorizacion {
        normalizaciones = List.copyOf(normalizaciones);
        penalizacionesAplicadas = List.copyOf(penalizacionesAplicadas);
    }
}
