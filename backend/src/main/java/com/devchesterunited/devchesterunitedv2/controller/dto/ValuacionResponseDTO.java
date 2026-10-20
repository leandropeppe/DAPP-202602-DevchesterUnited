package com.devchesterunited.devchesterunitedv2.controller.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;

public record ValuacionResponseDTO(String playerId, BigDecimal score, BigDecimal value,
        Instant calculatedAt, String strategyId, int strategyVersion, boolean visible,
        String strategyType, String position, JsonNode trace) { }
