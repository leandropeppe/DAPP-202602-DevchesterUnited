package com.devchesterunited.devchesterunitedv2.modelo.simulacion;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador;

import java.util.Objects;

public record EstadisticasProyectadas(MetricasEstadisticasJugador metricas) {
    public EstadisticasProyectadas {
        Objects.requireNonNull(metricas, "Las estadísticas proyectadas son obligatorias.");
    }

    public MetricasEstadisticasJugador estadisticas() {
        return metricas;
    }
}
