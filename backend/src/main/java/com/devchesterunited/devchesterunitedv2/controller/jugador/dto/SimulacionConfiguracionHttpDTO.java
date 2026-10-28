package com.devchesterunited.devchesterunitedv2.controller.jugador.dto;

import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.EstadisticaSimulacionDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.SimulacionConfiguracionDTO;

import java.math.BigDecimal;
import java.util.List;

public record SimulacionConfiguracionHttpDTO(String playerId, List<String> positions,
        String resolvedPosition, String strategyId, int strategyVersion, boolean provisionalStrategy,
        BigDecimal currentQuote, List<Integer> horizons, Integer currentApps, Integer currentMinutes,
        List<EstadisticaSimulacionDTO> statistics, List<String> blockingReasons) {
    public static SimulacionConfiguracionHttpDTO from(SimulacionConfiguracionDTO source) {
        return new SimulacionConfiguracionHttpDTO(source.playerId(), source.positions(), source.resolvedPosition(),
                source.strategyId(), source.strategyVersion(), source.provisionalStrategy(), source.currentQuote(),
                source.horizons(), source.currentApps(), source.currentMinutes(), source.statistics(),
                source.blockingReasons());
    }
}
