package com.devchesterunited.devchesterunitedv2.support.fixtures.valorizacion;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasDefensivas;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasGenerales;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasOfensivas;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasPases;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.EstadisticasProyectadas;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.EscenarioSimulacion;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.ConfiguracionElegibilidad;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaGeneralV1;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaValorizacion;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.JugadorEstadistico;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.MetricaValorizacion;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Fixtures aislados para T014-T017; no representan datos persistidos. */
public final class SimulacionFixtures {
    public static final UUID JUGADOR_DELANTERO_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID JUGADOR_POBLACION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private SimulacionFixtures() {
    }

    public static JugadorEstadistico delantero() {
        return new JugadorEstadistico(JUGADOR_DELANTERO_ID, "Forward", metricas(5, 3, 2.10, 1.20, 4.40, 1.10, 7.50));
    }

    public static JugadorEstadistico jugadorPoblacion() {
        return new JugadorEstadistico(JUGADOR_POBLACION_ID, "Forward", metricas(8, 4, 3.10, 2.20, 5.40, 2.10, 8.20));
    }

    public static List<JugadorEstadistico> poblacionOficial() {
        return List.of(delantero(), jugadorPoblacion());
    }

    public static EscenarioSimulacion escenario() {
        return new EscenarioSimulacion(JUGADOR_DELANTERO_ID, 5,
                Map.of(MetricaValorizacion.GOALS, BigDecimal.valueOf(3)));
    }

    public static EstadisticasProyectadas estadisticasProyectadas() {
        return com.devchesterunited.devchesterunitedv2.modelo.simulacion.ProyectorEstadisticas.proyectar(
                delantero().metricas(), escenario());
    }

    public static EstrategiaValorizacion estrategia() {
        return new EstrategiaGeneralV1(BigDecimal.ZERO, BigDecimal.ONE);
    }

    public static BigDecimal cotizacionActual() {
        return BigDecimal.valueOf(100);
    }

    public static ConfiguracionElegibilidad elegibilidad() {
        return ConfiguracionElegibilidad.sinUmbral();
    }

    private static MetricasEstadisticasJugador metricas(int goles, int asistencias, double tiros,
            double pasesClave, double regates, double tackles, double rating) {
        return new MetricasEstadisticasJugador(
                new MetricasGenerales(10, 1, 900, 1, bd(3.2), bd(rating), 1, 0),
                new MetricasOfensivas(goles, asistencias, bd(tiros), bd(pasesClave), bd(regates), bd(.2), bd(.1), bd(.3), bd(.2)),
                new MetricasDefensivas(bd(tackles), bd(1.2), bd(.4), bd(.2), bd(2.4), bd(.3), bd(.2), 0),
                new MetricasPases(bd(40), bd(82), bd(2), bd(5), bd(1)));
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
