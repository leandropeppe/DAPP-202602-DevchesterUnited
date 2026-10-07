package com.devchesterunited.devchesterunitedv2.adapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/** Formato externo aislado del Adapter. No atraviesa el puerto de estadísticas. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WhoScoredStatisticsResponseDTO(
        @JsonProperty("page") Integer page,
        @JsonProperty("pageSize") Integer pageSize,
        @JsonProperty("totalPages") Integer totalPages,
        @JsonProperty("totalResults") Integer totalResults,
        @JsonProperty("players") List<PlayerDTO> players) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlayerDTO(
            @JsonProperty("playerId") Long playerId,
            @JsonProperty("name") String name,
            @JsonProperty("position") String position,
            @JsonProperty("nationality") String nationality,
            @JsonProperty("teamId") Long teamId,
            @JsonProperty("teamName") String teamName,
            @JsonProperty("minutesPlayed") Integer minutesPlayed,
            @JsonProperty("goals") Integer goals,
            @JsonProperty("assists") Integer assists,
            @JsonProperty("shots") BigDecimal shots,
            @JsonProperty("keyPasses") BigDecimal keyPasses,
            @JsonProperty("dribbles") BigDecimal dribbles,
            @JsonProperty("tackles") BigDecimal tackles,
            @JsonProperty("rating") BigDecimal rating,
            @JsonProperty("yellowCards") Integer yellowCards,
            @JsonProperty("redCards") Integer redCards) { }
}
