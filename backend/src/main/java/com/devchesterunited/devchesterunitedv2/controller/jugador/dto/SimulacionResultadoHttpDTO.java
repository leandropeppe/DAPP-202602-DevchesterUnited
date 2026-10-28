package com.devchesterunited.devchesterunitedv2.controller.jugador.dto;

import com.devchesterunited.devchesterunitedv2.service.dto.simulacion.SimulacionResultadoDTO;

import java.math.BigDecimal;
import java.util.Map;

public record SimulacionResultadoHttpDTO(String playerId, int horizonMatches, String strategyId,
        int strategyVersion, String resolvedPosition, BigDecimal currentQuote, BigDecimal simulatedQuote,
        BigDecimal score, BigDecimal monetaryDifference, BigDecimal percentageDifference,
        String percentageDifferenceUnavailableReason, Map<String, Object> projectedStatistics,
        Map<String, Object> trace) {
    public static SimulacionResultadoHttpDTO from(SimulacionResultadoDTO source) {
        return new SimulacionResultadoHttpDTO(source.playerId(), source.horizonMatches(), source.strategyId(),
                source.strategyVersion(), source.resolvedPosition(), source.currentQuote(), source.simulatedQuote(),
                source.score(), source.monetaryDifference(), source.percentageDifference(),
                source.percentageDifferenceUnavailableReason(), source.projectedStatistics(), source.trace());
    }
}
