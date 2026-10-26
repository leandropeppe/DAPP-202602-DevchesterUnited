package com.devchesterunited.devchesterunitedv2.modelo.valorizacion;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador;
import java.util.Objects;
import java.util.UUID;

public record JugadorEstadistico(UUID jugadorId, String posicion, MetricasEstadisticasJugador metricas) {
    public JugadorEstadistico {
        Objects.requireNonNull(jugadorId, "El jugador es obligatorio.");
        Objects.requireNonNull(metricas, "Las métricas son obligatorias.");
    }
}
