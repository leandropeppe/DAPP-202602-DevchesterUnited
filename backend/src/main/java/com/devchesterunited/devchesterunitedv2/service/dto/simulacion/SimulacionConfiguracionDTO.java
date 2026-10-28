package com.devchesterunited.devchesterunitedv2.service.dto.simulacion;

import java.math.BigDecimal;
import java.util.List;

public record SimulacionConfiguracionDTO(String playerId, List<String> positions,
        String resolvedPosition, String strategyId, int strategyVersion, boolean provisionalStrategy,
        BigDecimal currentQuote, List<Integer> horizons, Integer currentApps, Integer currentMinutes,
        List<EstadisticaSimulacionDTO> statistics, List<String> blockingReasons) {
    public SimulacionConfiguracionDTO {
        positions = List.copyOf(positions == null ? List.of() : positions);
        horizons = List.copyOf(horizons == null ? List.of(3, 5, 8, 10) : horizons);
        statistics = List.copyOf(statistics == null ? List.of() : statistics);
        blockingReasons = List.copyOf(blockingReasons == null ? List.of() : blockingReasons);
    }
}
