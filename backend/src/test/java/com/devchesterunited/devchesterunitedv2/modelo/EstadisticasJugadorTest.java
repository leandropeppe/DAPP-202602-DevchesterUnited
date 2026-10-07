package com.devchesterunited.devchesterunitedv2.modelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EstadisticasJugadorTest {
    @Test
    void conservaMetricasAusentesSinInventarValores() {
        var metricas = new MetricasEstadisticasJugador(
                900, null, 2, null, BigDecimal.valueOf(1.5), null,
                BigDecimal.valueOf(2), BigDecimal.valueOf(7.2), null, null);

        var estadisticas = new EstadisticasJugador(UUID.randomUUID(), "Forward", metricas);

        assertThat(estadisticas.getMetricas().goles()).isNull();
        assertThat(estadisticas.getMetricas().tiros()).isNull();
        assertThat(estadisticas.getMetricas().valoracion()).isEqualByComparingTo("7.2");
    }

    @Test
    void rechazaMetricaNegativa() {
        assertThatThrownBy(() -> new MetricasEstadisticasJugador(
                -1, null, null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaJugadorNulo() {
        var metricas = new MetricasEstadisticasJugador(
                null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> new EstadisticasJugador(null, null, metricas))
                .isInstanceOf(NullPointerException.class);
    }
}
