package com.devchesterunited.devchesterunitedv2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.devchesterunited.devchesterunitedv2.modelo.MetricasDefensivas;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasGenerales;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasOfensivas;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasPases;
import com.devchesterunited.devchesterunitedv2.service.impl.ConsolidadorEstadisticasJugador;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ConsolidadorEstadisticasJugadorCompleteTest {
    @Test
    void combinaLasCuatroFamiliasYUsaElDatoNoNuloDeLaFuenteCanonica() {
        var anterior = new MetricasEstadisticasJugador(
                new MetricasGenerales(null, null, 800, null, null, new BigDecimal("6.5"), null, null),
                new MetricasOfensivas(null, null, null, new BigDecimal("1.0"), null, null, null, null, null),
                new MetricasDefensivas(null, null, null, null, null, null, null, null),
                new MetricasPases(null, null, null, null, null));
        var nueva = new MetricasEstadisticasJugador(
                new MetricasGenerales(20, null, null, null, null, null, null, null),
                new MetricasOfensivas(null, 5, null, null, null, null, null, null, null),
                new MetricasDefensivas(null, null, null, null, null, null, null, 1),
                new MetricasPases(null, new BigDecimal("86.44"), null, null, null));

        var resultado = new ConsolidadorEstadisticasJugador().conservar(anterior, nueva);

        assertEquals(20, resultado.generales().apps());
        assertEquals(800, resultado.generales().minsPlayed());
        assertEquals(5, resultado.ofensivas().assistTotal());
        assertEquals(1, resultado.defensivas().goalOwn());
        assertEquals(new BigDecimal("86.44"), resultado.pases().passSuccess());
    }
}
