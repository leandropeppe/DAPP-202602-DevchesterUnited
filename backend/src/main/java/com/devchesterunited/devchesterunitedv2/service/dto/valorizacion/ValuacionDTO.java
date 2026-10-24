package com.devchesterunited.devchesterunitedv2.service.dto.valorizacion;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;

public record ValuacionDTO(String playerId, BigDecimal score, BigDecimal value,
        Instant calculatedAt, String strategyId, int strategyVersion, boolean visible,
        String strategyType, String position, JsonNode trace) { }
