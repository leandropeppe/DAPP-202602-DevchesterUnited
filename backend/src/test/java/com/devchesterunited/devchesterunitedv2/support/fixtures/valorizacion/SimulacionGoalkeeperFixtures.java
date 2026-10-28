package com.devchesterunited.devchesterunitedv2.support.fixtures.valorizacion;

import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.JugadorEstadistico;

/** Fixture GK limitado a las métricas confirmadas por research.md. */
public final class SimulacionGoalkeeperFixtures {
    private SimulacionGoalkeeperFixtures() { }
    public static JugadorEstadistico completo() { return SimulacionPosicionesFixtures.goalkeeper(); }
    public static JugadorEstadistico sinLongPasses() {
        return new JugadorEstadistico(completo().jugadorId(), completo().posicion(),
                SimulacionPosicionesFixtures.metricasGkSinPasesLargos());
    }
}
