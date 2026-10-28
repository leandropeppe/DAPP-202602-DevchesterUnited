package com.devchesterunited.devchesterunitedv2.modelo.simulacion;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasDefensivas;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasGenerales;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasOfensivas;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasPases;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.MetricaValorizacion;

import java.math.BigDecimal;
import java.util.Map;

public final class ProyectorEstadisticas {
    private ProyectorEstadisticas() {
    }

    public static EstadisticasProyectadas proyectar(MetricasEstadisticasJugador actuales,
            EscenarioSimulacion escenario) {
        if (actuales == null) {
            throw new DatosInsuficientesException("estadísticas actuales");
        }
        if (actuales.generales().apps() == null) {
            throw new DatosInsuficientesException("apps actuales");
        }
        if (actuales.generales().minsPlayed() == null) {
            throw new DatosInsuficientesException("minsPlayed actuales");
        }

        Map<MetricaValorizacion, BigDecimal> modificaciones = escenario.modificaciones();
        MetricasGenerales generales = new MetricasGenerales(
                actuales.generales().apps() + escenario.horizontePartidos(),
                actuales.generales().subOn(),
                actuales.generales().minsPlayed() + escenario.horizontePartidos() * 90,
                entero(actuales, modificaciones, MetricaValorizacion.MAN_OF_THE_MATCH),
                decimal(actuales, modificaciones, MetricaValorizacion.AERIAL_WON),
                decimal(actuales, modificaciones, MetricaValorizacion.RATING),
                entero(actuales, modificaciones, MetricaValorizacion.YELLOW_CARDS),
                entero(actuales, modificaciones, MetricaValorizacion.RED_CARDS));

        MetricasOfensivas ofensivas = new MetricasOfensivas(
                entero(actuales, modificaciones, MetricaValorizacion.GOALS),
                entero(actuales, modificaciones, MetricaValorizacion.ASSISTS),
                decimal(actuales, modificaciones, MetricaValorizacion.SHOTS),
                decimal(actuales, modificaciones, MetricaValorizacion.KEY_PASSES),
                actuales.ofensivas().dribbleWonPerGame(),
                actuales.ofensivas().foulGivenPerGame(),
                actuales.ofensivas().offsideGivenPerGame(),
                actuales.ofensivas().dispossessedPerGame(),
                actuales.ofensivas().turnoverPerGame());

        MetricasDefensivas defensivas = new MetricasDefensivas(
                decimal(actuales, modificaciones, MetricaValorizacion.TACKLES),
                decimal(actuales, modificaciones, MetricaValorizacion.INTERCEPTIONS),
                actuales.defensivas().foulsPerGame(),
                actuales.defensivas().offsideWonPerGame(),
                decimal(actuales, modificaciones, MetricaValorizacion.CLEARANCES),
                actuales.defensivas().wasDribbledPerGame(),
                actuales.defensivas().outfielderBlockPerGame(),
                entero(actuales, modificaciones, MetricaValorizacion.GOAL_OWN));

        MetricasPases pases = new MetricasPases(
                actuales.pases().totalPassesPerGame(),
                decimal(actuales, modificaciones, MetricaValorizacion.PASS_SUCCESS),
                actuales.pases().accurateCrossesPerGame(),
                actuales.pases().accurateLongPassPerGame(),
                actuales.pases().accurateThroughBallPerGame());

        return new EstadisticasProyectadas(new MetricasEstadisticasJugador(generales, ofensivas, defensivas, pases));
    }

    private static Integer entero(MetricasEstadisticasJugador actuales,
            Map<MetricaValorizacion, BigDecimal> modificaciones, MetricaValorizacion metrica) {
        BigDecimal valor = valor(actuales, modificaciones, metrica);
        return valor == null ? null : valor.intValueExact();
    }

    private static BigDecimal decimal(MetricasEstadisticasJugador actuales,
            Map<MetricaValorizacion, BigDecimal> modificaciones, MetricaValorizacion metrica) {
        return valor(actuales, modificaciones, metrica);
    }

    private static BigDecimal valor(MetricasEstadisticasJugador actuales,
            Map<MetricaValorizacion, BigDecimal> modificaciones, MetricaValorizacion metrica) {
        BigDecimal actual = metrica.extraer(actuales);
        BigDecimal modificacion = modificaciones.get(metrica);
        if (modificacion == null) {
            return actual;
        }
        if (actual == null) {
            throw new DatosInsuficientesException(metrica.name());
        }
        if (CatalogoMetricaSimulacion.esAcumulativa(metrica)) {
            return actual.add(modificacion);
        }
        return modificacion;
    }
}
