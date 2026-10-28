package com.devchesterunited.devchesterunitedv2.service.impl.simulacion;

import com.devchesterunited.devchesterunitedv2.modelo.simulacion.EstadisticasProyectadas;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.JugadorEstadistico;

import java.util.Objects;
import java.util.UUID;

public final class JugadorEstadisticoSimulacionAdapter {
    private JugadorEstadisticoSimulacionAdapter() {
    }

    public static JugadorEstadistico adaptar(UUID jugadorId, String posicion,
            EstadisticasProyectadas proyectadas) {
        return new JugadorEstadistico(Objects.requireNonNull(jugadorId, "El jugador es obligatorio."),
                posicion, Objects.requireNonNull(proyectadas, "Las estadísticas proyectadas son obligatorias.").metricas());
    }
}
