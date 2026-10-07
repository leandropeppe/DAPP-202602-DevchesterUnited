package com.devchesterunited.devchesterunitedv2.modelo;

import java.util.Objects;

/** Registro normalizado que cruza el puerto estadístico sin exponer WhoScored. */
public record RegistroEstadisticasJugador(
        IdentidadEstadisticaJugador identidad,
        FamiliaEstadistica familia,
        MetricasEstadisticasJugador metricas) {

    public RegistroEstadisticasJugador {
        Objects.requireNonNull(identidad, "La identidad estadística es obligatoria.");
        Objects.requireNonNull(familia, "La familia estadística es obligatoria.");
        Objects.requireNonNull(metricas, "Las métricas estadísticas son obligatorias.");
    }
}
