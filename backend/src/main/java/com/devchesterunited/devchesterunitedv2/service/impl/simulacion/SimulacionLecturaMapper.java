package com.devchesterunited.devchesterunitedv2.service.impl.simulacion;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasDefensivas;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasGenerales;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasOfensivas;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasPases;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.EstadisticasProyectadas;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.ResultadoSimulacion;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaValorizacion;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.MetricaValorizacion;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.EstadisticaSimulacionDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.SimulacionConfiguracionDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.SimulacionResultadoDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.estadisticas.EstadisticasJugadorResultado;

import java.math.BigDecimal;
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
                generales(dto, g), ofensivas(dto, o), defensivas(dto, d), pases(p));
    }

    private static MetricasGenerales generales(EstadisticasJugadorResultado dto,
            EstadisticasJugadorResultado.Generales generales) {
        return new MetricasGenerales(value(generales == null ? null : generales.apps(), dto.minutosJugados()),
                generales == null ? null : generales.subOn(),
                value(generales == null ? null : generales.minsPlayed(), dto.minutosJugados()),
                generales == null ? null : generales.manOfTheMatch(),
                generales == null ? null : generales.aerialWonPerGame(),
                value(generales == null ? null : generales.rating(), dto.valoracion()),
                generales == null ? null : generales.yellowCard(),
                generales == null ? null : generales.redCard());
    }

    private static MetricasOfensivas ofensivas(EstadisticasJugadorResultado dto,
            EstadisticasJugadorResultado.Ofensivas ofensivas) {
        return new MetricasOfensivas(value(ofensivas == null ? null : ofensivas.goal(), dto.goles()),
                value(ofensivas == null ? null : ofensivas.assistTotal(), dto.asistencias()),
                value(ofensivas == null ? null : ofensivas.shotsPerGame(), dto.tiros()),
                value(ofensivas == null ? null : ofensivas.keyPassPerGame(), dto.pasesClave()),
                value(ofensivas == null ? null : ofensivas.dribbleWonPerGame(), dto.regates()),
                ofensivas == null ? null : ofensivas.foulGivenPerGame(),
                ofensivas == null ? null : ofensivas.offsideGivenPerGame(),
                ofensivas == null ? null : ofensivas.dispossessedPerGame(),
                ofensivas == null ? null : ofensivas.turnoverPerGame());
    }

    private static MetricasDefensivas defensivas(EstadisticasJugadorResultado dto,
            EstadisticasJugadorResultado.Defensivas defensivas) {
        return new MetricasDefensivas(value(defensivas == null ? null : defensivas.tacklePerGame(), dto.tackles()),
                defensivas == null ? null : defensivas.interceptionPerGame(),
                defensivas == null ? null : defensivas.foulsPerGame(),
                defensivas == null ? null : defensivas.offsideWonPerGame(),
                defensivas == null ? null : defensivas.clearancePerGame(),
                defensivas == null ? null : defensivas.wasDribbledPerGame(),
                defensivas == null ? null : defensivas.outfielderBlockPerGame(),
                defensivas == null ? null : defensivas.goalOwn());
    }

    private static MetricasPases pases(EstadisticasJugadorResultado.Pases pases) {
        return new MetricasPases(pases == null ? null : pases.totalPassesPerGame(),
                pases == null ? null : pases.passSuccess(),
                pases == null ? null : pases.accurateCrossesPerGame(),
                pases == null ? null : pases.accurateLongPassPerGame(),
                pases == null ? null : pases.accurateThroughBallPerGame());
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
        String unavailableReason;
        if (actual == null) {
            unavailableReason = "COTIZACION_ACTUAL_AUSENTE";
        } else if (actual.signum() == 0) {
            unavailableReason = "COTIZACION_ACTUAL_CERO";
        } else {
            unavailableReason = null;
        }
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
