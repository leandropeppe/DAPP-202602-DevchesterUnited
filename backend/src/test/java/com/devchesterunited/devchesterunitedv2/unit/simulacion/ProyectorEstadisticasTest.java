package com.devchesterunited.devchesterunitedv2.unit.simulacion;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasDefensivas;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasGenerales;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasOfensivas;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasPases;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.EstadisticasProyectadas;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.EscenarioSimulacion;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.CatalogoMetricaSimulacion;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.DatosInsuficientesException;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.MetricaNoEditableException;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.ProyectorEstadisticas;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.MetricaValorizacion;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProyectorEstadisticasTest {
    @Test
    void proyectaLosCuatroHorizontesYLasMetricasSegunSuSemantica() {
        for (int horizonte : new int[] { 3, 5, 8, 10 }) {
            MetricasEstadisticasJugador actuales = actuales();
            EscenarioSimulacion escenario = new EscenarioSimulacion(UUID.randomUUID(), horizonte, Map.ofEntries(
                    Map.entry(MetricaValorizacion.GOALS, BigDecimal.valueOf(3)),
                    Map.entry(MetricaValorizacion.ASSISTS, BigDecimal.valueOf(2)),
                    Map.entry(MetricaValorizacion.MAN_OF_THE_MATCH, BigDecimal.ONE),
                    Map.entry(MetricaValorizacion.YELLOW_CARDS, BigDecimal.ONE),
                    Map.entry(MetricaValorizacion.RED_CARDS, BigDecimal.ZERO),
                    Map.entry(MetricaValorizacion.GOAL_OWN, BigDecimal.ONE),
                    Map.entry(MetricaValorizacion.SHOTS, BigDecimal.valueOf(4.25)),
                    Map.entry(MetricaValorizacion.KEY_PASSES, BigDecimal.valueOf(3.75)),
                    Map.entry(MetricaValorizacion.TACKLES, BigDecimal.valueOf(2.50)),
                    Map.entry(MetricaValorizacion.INTERCEPTIONS, BigDecimal.valueOf(1.50)),
                    Map.entry(MetricaValorizacion.CLEARANCES, BigDecimal.valueOf(5.50)),
                    Map.entry(MetricaValorizacion.AERIAL_WON, BigDecimal.valueOf(6.25)),
                    Map.entry(MetricaValorizacion.PASS_SUCCESS, BigDecimal.valueOf(88.5)),
                    Map.entry(MetricaValorizacion.RATING, BigDecimal.valueOf(8.25))));

            EstadisticasProyectadas resultado = ProyectorEstadisticas.proyectar(actuales, escenario);
            MetricasEstadisticasJugador proyectadas = resultado.metricas();

            assertEquals(10 + horizonte, proyectadas.generales().apps());
            assertEquals(900 + horizonte * 90, proyectadas.generales().minsPlayed());
            assertEquals(8, proyectadas.ofensivas().goal());
            assertEquals(5, proyectadas.ofensivas().assistTotal());
            assertEquals(2, proyectadas.generales().manOfTheMatch());
            assertEquals(3, proyectadas.generales().yellowCard());
            assertEquals(1, proyectadas.generales().redCard());
            assertEquals(2, proyectadas.defensivas().goalOwn());
            assertEquals(new BigDecimal("4.25"), proyectadas.ofensivas().shotsPerGame());
            assertEquals(new BigDecimal("3.75"), proyectadas.ofensivas().keyPassPerGame());
            assertEquals(new BigDecimal("2.50"), proyectadas.defensivas().tacklePerGame());
            assertEquals(new BigDecimal("1.50"), proyectadas.defensivas().interceptionPerGame());
            assertEquals(new BigDecimal("5.50"), proyectadas.defensivas().clearancePerGame());
            assertEquals(new BigDecimal("6.25"), proyectadas.generales().aerialWonPerGame());
            assertEquals(0, new BigDecimal("88.50").compareTo(proyectadas.pases().passSuccess()));
            assertEquals(new BigDecimal("8.25"), proyectadas.generales().rating());
        }
    }

    @Test
    void clasificaElCatalogoSinInferirMetricasAcumulativas() {
        assertEquals(CatalogoMetricaSimulacion.Semantica.ACUMULATIVA,
                CatalogoMetricaSimulacion.definir(MetricaValorizacion.GOALS).semantica());
        assertEquals(CatalogoMetricaSimulacion.Semantica.ACUMULATIVA,
                CatalogoMetricaSimulacion.definir(MetricaValorizacion.GOAL_OWN).semantica());
        assertEquals(CatalogoMetricaSimulacion.Semantica.POR_PARTIDO,
                CatalogoMetricaSimulacion.definir(MetricaValorizacion.SHOTS).semantica());
        assertEquals(CatalogoMetricaSimulacion.Semantica.PORCENTAJE,
                CatalogoMetricaSimulacion.definir(MetricaValorizacion.PASS_SUCCESS).semantica());
        assertEquals(CatalogoMetricaSimulacion.Semantica.RATING,
                CatalogoMetricaSimulacion.definir(MetricaValorizacion.RATING).semantica());
    }

    @Test
    void conservaMetricasNoEnviadasIncluyendoNulos() {
        MetricasEstadisticasJugador actuales = actuales();
        EscenarioSimulacion escenario = new EscenarioSimulacion(UUID.randomUUID(), 5,
                Map.of(MetricaValorizacion.GOALS, BigDecimal.ONE));

        MetricasEstadisticasJugador resultado = ProyectorEstadisticas.proyectar(actuales, escenario).metricas();

        assertEquals(actuales.ofensivas().shotsPerGame(), resultado.ofensivas().shotsPerGame());
        assertEquals(actuales.pases().accurateCrossesPerGame(), resultado.pases().accurateCrossesPerGame());
        assertEquals(actuales.ofensivas().foulGivenPerGame(), resultado.ofensivas().foulGivenPerGame());
        assertEquals(actuales.generales().subOn(), resultado.generales().subOn());
    }

    @Test
    void rechazaAppsYMinutosComoModificaciones() {
        assertThrows(MetricaNoEditableException.class,
                () -> ProyectorEstadisticas.proyectar(actuales(), EscenarioSimulacion.conModificacionNoTipada(
                        UUID.randomUUID(), 5, "apps", BigDecimal.ONE)));
    }

    @Test
    void rechazaAppsOMinutosActualesNulos() {
        MetricasEstadisticasJugador sinApps = new MetricasEstadisticasJugador(
                new MetricasGenerales(null, 2, 900, 1, null, new BigDecimal("7.50"), 2, 1),
                new MetricasOfensivas(5, 3, null, null, null, null, null, null, null),
                new MetricasDefensivas(null, null, null, null, null, null, null, null),
                new MetricasPases(null, null, null, null, null));
        assertThrows(DatosInsuficientesException.class,
                () -> ProyectorEstadisticas.proyectar(sinApps,
                        new EscenarioSimulacion(UUID.randomUUID(), 3, Map.of())));
    }

    private static MetricasEstadisticasJugador actuales() {
        return new MetricasEstadisticasJugador(
                new MetricasGenerales(10, 2, 900, 1, new BigDecimal("6.25"), new BigDecimal("7.50"), 2, 1),
                new MetricasOfensivas(5, 3, new BigDecimal("2.10"), new BigDecimal("1.20"), new BigDecimal("4.40"),
                        null, null, null, null),
                new MetricasDefensivas(new BigDecimal("1.10"), new BigDecimal("0.80"), null, null,
                        new BigDecimal("3.20"), null, null, 1),
                new MetricasPases(new BigDecimal("40.00"), new BigDecimal("75.00"), null,
                        new BigDecimal("5.00"), null));
    }
}
