package com.devchesterunited.devchesterunitedv2.unit.valorizacion;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasDefensivas;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasGenerales;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasOfensivas;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasPases;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaPosicionalV1;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.JugadorEstadistico;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PenalizacionesPosicionalesTest {
    private final EstrategiaPosicionalV1 estrategia = new EstrategiaPosicionalV1(BigDecimal.ZERO, BigDecimal.ONE);

    @Test
    void conservaRojaSignificativaYWasDribbledSegunPosicion() {
        String[] posiciones = {"DL", "MC", "DF"};
        for (int i = 0; i < posiciones.length; i++) {
            String posicion = posiciones[i];
            assertEquals(d(".10"), estrategia.pesosPenalizacion(posicion).get(MetricaValorizacion.RED_CARDS));
        }
        assertTrue(estrategia.pesosPenalizacion("PO").isEmpty());
        assertEquals(BigDecimal.ZERO, estrategia.pesosPenalizacion("DL").get(MetricaValorizacion.WAS_DRIBBLED));
        assertEquals(d(".03"), estrategia.pesosPenalizacion("MC").get(MetricaValorizacion.WAS_DRIBBLED));
        assertEquals(d(".08"), estrategia.pesosPenalizacion("DF").get(MetricaValorizacion.WAS_DRIBBLED));
        assertTrue(estrategia.pesosPenalizacion("DF").get(MetricaValorizacion.WAS_DRIBBLED)
                .compareTo(estrategia.pesosPenalizacion("MC").get(MetricaValorizacion.WAS_DRIBBLED)) > 0);
    }

    @Test
    void normalizaPorPosicionYElGolDeDefensorAportaScore() {
        MotorValorizacion motor = new MotorValorizacion();
        JugadorEstadistico dfBajo = jugador("DF", 0, 0, 0, 0);
        JugadorEstadistico dfAlto = jugador("DF", 1, 1, 0, 0);
        JugadorEstadistico dlExtremo = jugador("DL", 100, 100, 0, 0);

        ResultadoValorizacion resultado = motor.calcular(estrategia,
                List.of(dfBajo, dfAlto, dlExtremo), ConfiguracionElegibilidad.sinUmbral()).stream()
                .filter(item -> item.jugadorId().equals(dfAlto.jugadorId())).findFirst().orElseThrow();
        ResultadoNormalizacion goals = resultado.normalizaciones().stream()
                .filter(item -> item.metrica() == MetricaValorizacion.GOALS).findFirst().orElseThrow();

        assertEquals("POSICION", goals.alcance());
        assertEquals(0, goals.minimo().compareTo(d("0")));
        assertEquals(0, goals.maximo().compareTo(d("1")));
        assertTrue(resultado.score().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void aplicaPenalizacionDeRojaYWasDribbledConElImpactoDefinido() {
        MotorValorizacion motor = new MotorValorizacion();
        String[] posiciones = {"DL", "MC", "DF"};
        String[] scoresPrevios = {".90", ".90", ".90"};
        String[] scoresFinales = {"1.00", ".95", ".86"};
        for (int i = 0; i < posiciones.length; i++) {
            String posicion = posiciones[i];
            JugadorEstadistico bajo = jugador(posicion, 0, 0, 0, 0);
            JugadorEstadistico altoConRoja = jugador(posicion, 1, 1, 1, 0);
            ResultadoValorizacion resultado = motor.calcular(estrategia, List.of(bajo, altoConRoja),
                    ConfiguracionElegibilidad.sinUmbral()).stream()
                    .filter(item -> item.jugadorId().equals(altoConRoja.jugadorId())).findFirst().orElseThrow();
            assertEquals(d(scoresPrevios[i]), resultado.scorePosicionalAntesAjuste().setScale(2));
            assertEquals(d(scoresFinales[i]), resultado.score().setScale(2, java.math.RoundingMode.HALF_UP));
        }

        assertEquals(d("1.00"), scoreConWasDribbled("DL").setScale(2, java.math.RoundingMode.HALF_UP));
        assertEquals(d("1.00"), scoreConWasDribbled("MC").setScale(2, java.math.RoundingMode.HALF_UP));
        assertEquals(d(".87"), scoreConWasDribbled("DF").setScale(2, java.math.RoundingMode.HALF_UP));
    }

    private BigDecimal scoreConWasDribbled(String posicion) {
        MotorValorizacion motor = new MotorValorizacion();
        JugadorEstadistico bajo = jugador(posicion, 0, 0, 0, 0);
        JugadorEstadistico alto = jugador(posicion, 1, 1, 0, 1);
        return motor.calcular(estrategia, List.of(bajo, alto), ConfiguracionElegibilidad.sinUmbral()).stream()
                .filter(item -> item.jugadorId().equals(alto.jugadorId())).findFirst().orElseThrow().score();
    }

    private static JugadorEstadistico jugador(String posicion, int valor, int goles, int roja, int wasDribbled) {
        BigDecimal v = d(Integer.toString(valor));
        MetricasEstadisticasJugador metricas = new MetricasEstadisticasJugador(
                new MetricasGenerales(10, 0, 900, valor, v, v, 0, roja),
                new MetricasOfensivas(goles, valor, v, v, v, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                new MetricasDefensivas(v, v, BigDecimal.ZERO, BigDecimal.ZERO, v, d(Integer.toString(wasDribbled)), v, 0),
                new MetricasPases(v, v, v, v, v));
        return new JugadorEstadistico(UUID.randomUUID(), posicion, metricas);
    }

    private static BigDecimal d(String value) { return new BigDecimal(value); }
}
