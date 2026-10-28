package com.devchesterunited.devchesterunitedv2.support.fixtures.valorizacion;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.JugadorEstadistico;

import java.util.UUID;

/** Datos completos y aislados para validar la configuración por posición. */
public final class SimulacionPosicionesFixtures {
    private SimulacionPosicionesFixtures() { }

    public static JugadorEstadistico forward() { return jugador("Forward", "11111111-1111-1111-1111-111111111101"); }
    public static JugadorEstadistico midfielder() { return jugador("Midfielder", "11111111-1111-1111-1111-111111111102"); }
    public static JugadorEstadistico defender() { return jugador("Defender", "11111111-1111-1111-1111-111111111103"); }
    public static JugadorEstadistico goalkeeper() { return jugador("Goalkeeper", "11111111-1111-1111-1111-111111111104"); }

    public static JugadorEstadistico jugador(String posicion, String id) {
        var base = SimulacionFixtures.delantero();
        return new JugadorEstadistico(UUID.fromString(id), posicion, base.metricas());
    }

    public static MetricasEstadisticasJugador metricasGkSinPasesLargos() {
        var base = goalkeeper().metricas();
        return new MetricasEstadisticasJugador(base.generales(), base.ofensivas(), base.defensivas(),
                new com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasPases(
                        base.pases().totalPassesPerGame(), base.pases().passSuccess(),
                        base.pases().accurateCrossesPerGame(), null, base.pases().accurateThroughBallPerGame()));
    }
}
