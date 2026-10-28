package com.devchesterunited.devchesterunitedv2.service.impl.simulacion;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasDefensivas;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasGenerales;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasOfensivas;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasPases;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.CatalogoMetricaSimulacion;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.ConfiguradorMetricasSimulacion;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.EstadisticasProyectadas;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.ResultadoSimulacion;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaValorizacion;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.MetricaValorizacion;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.EstadisticaSimulacionDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.SimulacionConfiguracionDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.SimulacionResultadoDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.estadisticas.EstadisticasJugadorResultado;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class SimulacionLecturaMapper {
    private final ConfiguradorMetricasSimulacion configurador = new ConfiguradorMetricasSimulacion();
    public MetricasEstadisticasJugador aModelo(EstadisticasJugadorResultado dto) {
        if (dto == null) return null;
        var g = dto.generales();
        var o = dto.ofensivas();
        var d = dto.defensivas();
        var p = dto.pases();
        return new MetricasEstadisticasJugador(
                new MetricasGenerales(value(g == null ? null : g.apps(), dto.minutosJugados()),
                        g == null ? null : g.subOn(), value(g == null ? null : g.minsPlayed(), dto.minutosJugados()),
                        g == null ? null : g.manOfTheMatch(), g == null ? null : g.aerialWonPerGame(),
                        value(g == null ? null : g.rating(), dto.valoracion()), g == null ? null : g.yellowCard(),
                        g == null ? null : g.redCard()),
                new MetricasOfensivas(value(o == null ? null : o.goal(), dto.goles()),
                        value(o == null ? null : o.assistTotal(), dto.asistencias()),
                        value(o == null ? null : o.shotsPerGame(), dto.tiros()),
                        value(o == null ? null : o.keyPassPerGame(), dto.pasesClave()),
                        value(o == null ? null : o.dribbleWonPerGame(), dto.regates()),
                        o == null ? null : o.foulGivenPerGame(), o == null ? null : o.offsideGivenPerGame(),
                        o == null ? null : o.dispossessedPerGame(), o == null ? null : o.turnoverPerGame()),
                new MetricasDefensivas(value(d == null ? null : d.tacklePerGame(), dto.tackles()),
                        d == null ? null : d.interceptionPerGame(), d == null ? null : d.foulsPerGame(),
                        d == null ? null : d.offsideWonPerGame(), d == null ? null : d.clearancePerGame(),
                        d == null ? null : d.wasDribbledPerGame(), d == null ? null : d.outfielderBlockPerGame(),
                        d == null ? null : d.goalOwn()),
                new MetricasPases(p == null ? null : p.totalPassesPerGame(), p == null ? null : p.passSuccess(),
                        p == null ? null : p.accurateCrossesPerGame(), p == null ? null : p.accurateLongPassPerGame(),
                        p == null ? null : p.accurateThroughBallPerGame()));
    }

    private static <T> T value(T nested, T fallback) { return nested != null ? nested : fallback; }

    public SimulacionConfiguracionDTO aConfiguracion(String playerId, Collection<String> positions,
            String resolvedPosition, EstrategiaValorizacion estrategia, BigDecimal currentQuote,
            MetricasEstadisticasJugador actuales, Collection<String> blockingReasons) {
        List<EstadisticaSimulacionDTO> statistics = configurador.configurar(resolvedPosition, estrategia, actuales);
        String strategyId = estrategia == null ? null : estrategia.id();
        int strategyVersion = estrategia == null ? 0 : estrategia.version();
        boolean provisional = estrategia != null
                && (estrategia.esProvisoria() || estrategia.esProvisoria(resolvedPosition));
        Integer apps = actuales == null ? null : actuales.generales().apps();
        Integer minutes = actuales == null ? null : actuales.generales().minsPlayed();
        return new SimulacionConfiguracionDTO(playerId, positions == null ? List.of() : List.copyOf(positions),
                resolvedPosition, strategyId, strategyVersion, provisional, currentQuote,
                List.of(3, 5, 8, 10), apps, minutes, statistics,
                blockingReasons == null ? List.of() : List.copyOf(blockingReasons));
    }

    public SimulacionResultadoDTO aResultado(ResultadoSimulacion resultado) {
        return aResultado(resultado, resultado.cotizacionActual());
    }

    public SimulacionResultadoDTO aResultado(ResultadoSimulacion resultado, BigDecimal currentQuote) {
        BigDecimal actual = currentQuote;
        BigDecimal simulated = resultado.cotizacionSimulada();
        BigDecimal monetary = actual == null || simulated == null ? null : simulated.subtract(actual);
        BigDecimal percentage = actual == null || simulated == null || actual.signum() == 0
                ? null : monetary.multiply(BigDecimal.valueOf(100)).divide(actual, 8, java.math.RoundingMode.HALF_UP);
        String unavailableReason = actual == null ? "COTIZACION_ACTUAL_AUSENTE"
                : actual.signum() == 0 ? "COTIZACION_ACTUAL_CERO" : null;
        return new SimulacionResultadoDTO("ply_" + resultado.jugadorId(), resultado.horizontePartidos(),
                resultado.estrategiaId(), resultado.estrategiaVersion(), resultado.posicion(), actual, simulated,
                resultado.score(), monetary, percentage, unavailableReason,
                estadisticas(resultado.estadisticasProyectadas()), Map.of());
    }

    private static Map<String, Object> estadisticas(EstadisticasProyectadas proyectadas) {
        MetricasEstadisticasJugador s = proyectadas.metricas();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("apps", s.generales().apps());
        values.put("minsPlayed", s.generales().minsPlayed());
        for (MetricaValorizacion metrica : MetricaValorizacion.values()) {
            values.put(metrica.name(), metrica.extraer(s));
        }
        return values;
    }
}
