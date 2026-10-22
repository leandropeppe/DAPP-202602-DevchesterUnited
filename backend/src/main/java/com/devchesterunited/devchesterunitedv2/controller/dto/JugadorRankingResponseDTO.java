package com.devchesterunited.devchesterunitedv2.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record JugadorRankingResponseDTO(int rank, String playerId, String nombre,
        String posicion, String equipo, BigDecimal cotizacion,
        @JsonProperty("strategyId") String strategyId,
        @JsonProperty("strategyVersion") int strategyVersion) { }
