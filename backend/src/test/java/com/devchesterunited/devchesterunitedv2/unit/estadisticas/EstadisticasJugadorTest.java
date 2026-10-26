package com.devchesterunited.devchesterunitedv2.unit.estadisticas;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.EstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThat(estadisticas.getMetricas().valoracion()).hasScaleOf(2);
    }

    @Test
    void normalizaTodasLasMetricasDecimalesConHalfUp() {
        var metricas = new MetricasEstadisticasJugador(
                null, null, null,
                new BigDecimal("0.6666666666666666"),
                new BigDecimal("1.23456789"),
                new BigDecimal("1.875"),
                new BigDecimal("2.555"),
                new BigDecimal("7.758333333333334"),
                null, null);

        assertThat(metricas.tiros()).isEqualTo(new BigDecimal("0.67"));
        assertThat(metricas.pasesClave()).isEqualTo(new BigDecimal("1.23"));
        assertThat(metricas.regates()).isEqualTo(new BigDecimal("1.88"));
        assertThat(metricas.tackles()).isEqualTo(new BigDecimal("2.56"));
        assertThat(metricas.valoracion()).isEqualTo(new BigDecimal("7.76"));
        assertThat(metricas.tiros()).hasScaleOf(2);
        assertThat(metricas.pasesClave()).hasScaleOf(2);
        assertThat(metricas.regates()).hasScaleOf(2);
        assertThat(metricas.tackles()).hasScaleOf(2);
        assertThat(metricas.valoracion()).hasScaleOf(2);

        var lessThanTwoDecimals = new MetricasEstadisticasJugador(
                null, null, null, new BigDecimal("1.2"), new BigDecimal("2"),
                null, null, new BigDecimal("7.2"), null, null);
        assertThat(lessThanTwoDecimals.tiros()).isEqualTo(new BigDecimal("1.20"));
        assertThat(lessThanTwoDecimals.pasesClave()).isEqualTo(new BigDecimal("2.00"));
        assertThat(lessThanTwoDecimals.valoracion()).isEqualTo(new BigDecimal("7.20"));

        Map<String, String> ratingCases = Map.of(
                "7.751", "7.75",
                "7.754", "7.75",
                "7.755", "7.76",
                "7.756", "7.76",
                "7.758333333333334", "7.76",
                "7.7583333333333337", "7.76",
                "7.999", "8.00");
        ratingCases.forEach((input, expected) -> {
            var ratingOnly = new MetricasEstadisticasJugador(
                    null, null, null, null, null, null, null,
                    new BigDecimal(input), null, null);
            assertThat(ratingOnly.valoracion()).isEqualTo(new BigDecimal(expected));
            assertThat(ratingOnly.valoracion()).hasScaleOf(2);
        });
    }

    @Test
    void conservaValoracionNulaComoAusenciaLegitima() {
        var metricas = new MetricasEstadisticasJugador(
                null, null, null, null, null, null, null, null, null, null);

        assertThat(metricas.valoracion()).isNull();
    }

    @Test
    void conservaSemanticaDeContadoresEnteros() {
        var metricas = new MetricasEstadisticasJugador(
                900, 8, 4, null, null, null, null,
                null, 2, 1);

        assertThat(metricas.minutosJugados()).isEqualTo(900);
        assertThat(metricas.goles()).isEqualTo(8);
        assertThat(metricas.asistencias()).isEqualTo(4);
        assertThat(metricas.tarjetasAmarillas()).isEqualTo(2);
        assertThat(metricas.tarjetasRojas()).isEqualTo(1);
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
