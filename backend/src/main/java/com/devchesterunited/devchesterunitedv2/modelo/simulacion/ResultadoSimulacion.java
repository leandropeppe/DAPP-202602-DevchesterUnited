package com.devchesterunited.devchesterunitedv2.modelo.simulacion;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record ResultadoSimulacion(UUID jugadorId, int horizontePartidos, String estrategiaId,
        int estrategiaVersion, String posicion, BigDecimal cotizacionActual,
        BigDecimal cotizacionSimulada, BigDecimal score,
        EstadisticasProyectadas estadisticasProyectadas) {
    public ResultadoSimulacion {
        Objects.requireNonNull(jugadorId, "El jugador es obligatorio.");
        Objects.requireNonNull(estrategiaId, "La estrategia es obligatoria.");
        Objects.requireNonNull(estadisticasProyectadas, "Las estadísticas proyectadas son obligatorias.");
    }
}
