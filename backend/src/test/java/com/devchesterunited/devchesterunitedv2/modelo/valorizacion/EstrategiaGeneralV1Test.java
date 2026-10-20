package com.devchesterunited.devchesterunitedv2.modelo.valorizacion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.devchesterunited.devchesterunitedv2.modelo.MetricasDefensivas;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasGenerales;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasOfensivas;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasPases;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EstrategiaGeneralV1Test {
    private final EstrategiaGeneralV1 estrategia = new EstrategiaGeneralV1(BigDecimal.ZERO, BigDecimal.ONE);

    @Test
    void conservaPesosExactosYFormulaGeneral() {
        assertEquals(Map.of(
                MetricaValorizacion.GOALS, d(".25"),
                MetricaValorizacion.ASSISTS, d(".15"),
                MetricaValorizacion.SHOTS, d(".10"),
                MetricaValorizacion.KEY_PASSES, d(".10"),
                MetricaValorizacion.DRIBBLES, d(".10"),
                MetricaValorizacion.TACKLES, d(".10"),
                MetricaValorizacion.RATING, d(".20")), estrategia.pesosPositivos("DF"));
        assertEquals(0, estrategia.pesosPositivos("DF").values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(BigDecimal.ONE));

        MotorValorizacion motor = new MotorValorizacion();
        var resultados = motor.calcular(estrategia, List.of(jugador(0), jugador(1)),
                ConfiguracionElegibilidad.sinUmbral());

        assertEquals(0, resultados.get(0).score().compareTo(BigDecimal.ZERO));
        assertEquals(0, resultados.get(1).score().compareTo(BigDecimal.ONE));
    }

    private static JugadorEstadistico jugador(int valor) {
        BigDecimal v = d(Integer.toString(valor));
        MetricasEstadisticasJugador metricas = new MetricasEstadisticasJugador(
                new MetricasGenerales(10, 0, 900, 1, v, v, 0, 0),
                new MetricasOfensivas(valor, valor, v, v, v, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                new MetricasDefensivas(v, v, BigDecimal.ZERO, BigDecimal.ZERO, v, BigDecimal.ZERO, v, 0),
                new MetricasPases(v, v, v, v, v));
        return new JugadorEstadistico(UUID.randomUUID(), "DF", metricas);
    }

    private static BigDecimal d(String value) { return new BigDecimal(value); }
}
