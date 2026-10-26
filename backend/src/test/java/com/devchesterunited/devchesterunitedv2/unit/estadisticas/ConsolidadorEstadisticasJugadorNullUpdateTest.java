package com.devchesterunited.devchesterunitedv2.modelo.estadisticas;

import static org.assertj.core.api.Assertions.assertThat;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasDefensivas;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasGenerales;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasOfensivas;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasPases;
import com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.ConsolidadorEstadisticasJugador;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ConsolidadorEstadisticasJugadorNullUpdateTest {
    @Test
    void unaMetricaAusentePermaneceNullYNoSeConvierteEnCero() {
        var anterior = metricas(null, null);
        var nueva = metricas(null, null);

        var resultado = new ConsolidadorEstadisticasJugador().conservar(anterior, nueva);

        assertThat(resultado.generales().rating()).isNull();
        assertThat(resultado.pases().passSuccess()).isNull();
    }

    private static MetricasEstadisticasJugador metricas(BigDecimal rating, BigDecimal passSuccess) {
        return new MetricasEstadisticasJugador(
                new MetricasGenerales(null, null, null, null, null, rating, null, null),
                new MetricasOfensivas(null, null, null, null, null, null, null, null, null),
                new MetricasDefensivas(null, null, null, null, null, null, null, null),
                new MetricasPases(null, passSuccess, null, null, null));
    }
}
