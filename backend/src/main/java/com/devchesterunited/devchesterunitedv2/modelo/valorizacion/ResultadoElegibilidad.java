package com.devchesterunited.devchesterunitedv2.modelo.valorizacion;

import java.util.List;
import java.util.UUID;

public record ResultadoElegibilidad(UUID jugadorId, String estrategiaId, int version,
        boolean elegible, String posicion, List<String> causasExclusion,
        List<MetricaValorizacion> metricasNegativasAusentes) {
    public ResultadoElegibilidad {
        causasExclusion = List.copyOf(causasExclusion);
        metricasNegativasAusentes = List.copyOf(metricasNegativasAusentes);
    }
}
