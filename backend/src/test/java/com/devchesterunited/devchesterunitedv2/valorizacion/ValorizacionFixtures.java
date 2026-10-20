package com.devchesterunited.devchesterunitedv2.valorizacion;

import com.devchesterunited.devchesterunitedv2.modelo.MetricasDefensivas;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasGenerales;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasOfensivas;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasPases;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.JugadorEstadistico;
import java.math.BigDecimal;
import java.util.UUID;

public final class ValorizacionFixtures {
    private ValorizacionFixtures() { }

    public static JugadorEstadistico jugador(String posicion, int goles, BigDecimal rating) {
        MetricasEstadisticasJugador metricas = new MetricasEstadisticasJugador(
                new MetricasGenerales(10, 0, 900, 1, decimal("1"), rating, 1, 0),
                new MetricasOfensivas(goles, 4, decimal("3"), decimal("2"), decimal("1"), decimal("1"), decimal("1"), decimal("1"), decimal("1")),
                new MetricasDefensivas(decimal("2"), decimal("1"), decimal("1"), decimal("0"), decimal("3"), decimal("0"), decimal("1"), 0),
                new MetricasPases(decimal("50"), decimal("80"), decimal("1"), decimal("4"), decimal("1")));
        return new JugadorEstadistico(UUID.randomUUID(), posicion, metricas);
    }

    public static MetricasEstadisticasJugador metricasSinRating() {
        return new MetricasEstadisticasJugador(
                new MetricasGenerales(10, 0, 900, 1, decimal("1"), null, 1, 0),
                new MetricasOfensivas(1, 1, decimal("1"), decimal("1"), decimal("1"), null, null, null, null),
                new MetricasDefensivas(decimal("1"), null, null, null, null, null, null, null),
                new MetricasPases(null, null, null, null, null));
    }

    private static BigDecimal decimal(String value) { return new BigDecimal(value); }
}
