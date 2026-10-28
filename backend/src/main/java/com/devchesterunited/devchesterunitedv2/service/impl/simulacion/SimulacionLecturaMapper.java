package com.devchesterunited.devchesterunitedv2.service.impl.simulacion;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.CatalogoMetricaSimulacion;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.EstadisticasProyectadas;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.ResultadoSimulacion;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaValorizacion;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.MetricaValorizacion;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.EstadisticaSimulacionDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.SimulacionConfiguracionDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.SimulacionResultadoDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.TipoMetricaSimulacionDTO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SimulacionLecturaMapper {
    public SimulacionConfiguracionDTO aConfiguracion(String playerId, Collection<String> positions,
            String resolvedPosition, EstrategiaValorizacion estrategia, BigDecimal currentQuote,
            MetricasEstadisticasJugador actuales, Collection<String> blockingReasons) {
        List<EstadisticaSimulacionDTO> statistics = new ArrayList<>();
        for (MetricaValorizacion metrica : MetricaValorizacion.values()) {
            CatalogoMetricaSimulacion.Definicion definicion = CatalogoMetricaSimulacion.definir(metrica);
            BigDecimal value = actuales == null ? null : metrica.extraer(actuales);
            boolean usadaPorEstrategia = estrategia != null
                    && (estrategia.pesosPositivos(resolvedPosition).containsKey(metrica)
                            || estrategia.pesosPenalizacion(resolvedPosition).containsKey(metrica));
            statistics.add(new EstadisticaSimulacionDTO(metrica.name(), definicion.etiqueta(),
                    tipo(definicion.semantica()), value, usadaPorEstrategia && value != null,
                    definicion.minimo(), definicion.maximo()));
        }
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
        BigDecimal actual = resultado.cotizacionActual();
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

    private static TipoMetricaSimulacionDTO tipo(CatalogoMetricaSimulacion.Semantica semantica) {
        return switch (semantica) {
            case ACUMULATIVA -> TipoMetricaSimulacionDTO.ACCUMULATIVE;
            case POR_PARTIDO -> TipoMetricaSimulacionDTO.AVERAGE;
            case PORCENTAJE -> TipoMetricaSimulacionDTO.PERCENTAGE;
            case RATING -> TipoMetricaSimulacionDTO.RATING;
        };
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
