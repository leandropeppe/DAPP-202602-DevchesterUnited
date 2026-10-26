package com.devchesterunited.devchesterunitedv2.unit.estadisticas;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasDefensivas;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasGenerales;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasOfensivas;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasPases;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;


class MetricasEstadisticasJugadorComposicionTest {
    @Test
    void componeLasCuatroFamiliasYConservaAusenciasComoNull() {
        var metricas = new MetricasEstadisticasJugador(
                new MetricasGenerales(10, 2, 900, null, new BigDecimal("1.2"), new BigDecimal("7.4"), 1, 0),
                new MetricasOfensivas(3, 4, new BigDecimal("2.1"), null, null, null, null, null, null),
                new MetricasDefensivas(null, null, null, null, null, null, null, null),
                new MetricasPases(new BigDecimal("44.2"), new BigDecimal("86.44"), null, null, null));

        assertEquals(10, metricas.generales().apps());
        assertEquals(3, metricas.ofensivas().goal());
        assertNull(metricas.defensivas().tacklePerGame());
        assertEquals(new BigDecimal("86.44"), metricas.pases().passSuccess());
    }

    @Test
    void rechazaMetricasNegativasYPorcentajeFueraDeRango() {
        var porcentajeFueraDeRango = new BigDecimal("100.01");
        assertThrows(IllegalArgumentException.class,
                () -> new MetricasGenerales(-1, null, null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new MetricasPases(null, porcentajeFueraDeRango, null, null, null));
    }
}
