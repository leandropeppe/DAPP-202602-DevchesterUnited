package com.devchesterunited.devchesterunitedv2.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.devchesterunited.devchesterunitedv2.modelo.MetricasDefensivas;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasGenerales;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasOfensivas;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasPases;
import org.junit.jupiter.api.Test;

class SincronizacionEstadisticasExistingPlayerRegressionTest {
    @Test
    void jugadorExistentePuedeRecibirLasCuatroFamiliasEnUnaComposicion() {
        var metricas = new MetricasEstadisticasJugador(
                new MetricasGenerales(1, 2, 3, 4, null, null, null, null),
                new MetricasOfensivas(5, 6, null, null, null, null, null, null, null),
                new MetricasDefensivas(null, null, null, null, null, null, null, 0),
                new MetricasPases(null, null, null, null, null));
        assertThat(metricas.generales().apps()).isEqualTo(1);
        assertThat(metricas.ofensivas().goal()).isEqualTo(5);
        assertThat(metricas.defensivas().goalOwn()).isZero();
        assertThat(metricas.pases()).isNotNull();
    }
}
