package com.devchesterunited.devchesterunitedv2.service.dto.simulacion;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public record SimulacionResultadoDTO(String playerId, int horizonMatches, String strategyId,
        int strategyVersion, String resolvedPosition, BigDecimal currentQuote,
        BigDecimal simulatedQuote, BigDecimal score, BigDecimal monetaryDifference,
        BigDecimal percentageDifference, String percentageDifferenceUnavailableReason,
        Map<String, Object> projectedStatistics, Map<String, Object> trace) {
    public SimulacionResultadoDTO {
        projectedStatistics = immutableNullableMap(projectedStatistics);
        trace = immutableNullableMap(trace);
    }

    private static Map<String, Object> immutableNullableMap(Map<String, Object> values) {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values == null ? Map.of() : values));
    }
}
