package com.devchesterunited.devchesterunitedv2.modelo;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Fotografía de estadísticas asociada a un Jugador existente. */
public final class EstadisticasJugador {
    private final UUID jugadorId;
    private final String posicion;
    private final MetricasEstadisticasJugador metricas;

    public EstadisticasJugador(UUID jugadorId, String posicion,
            MetricasEstadisticasJugador metricas) {
        this.jugadorId = Objects.requireNonNull(jugadorId, "El jugador es obligatorio.");
        this.posicion = posicion;
        this.metricas = Objects.requireNonNull(metricas, "Las métricas son obligatorias.");
        validarValoracion(metricas.valoracion());
    }

    private static void validarValoracion(BigDecimal valoracion) {
        if (valoracion != null && valoracion.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("La valoración no puede ser negativa.");
        }
    }

    public UUID getJugadorId() { return jugadorId; }
    public String getPosicion() { return posicion; }
    public MetricasEstadisticasJugador getMetricas() { return metricas; }
}
