package com.devchesterunited.devchesterunitedv2.adapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/** DTO interno del sobre JSON real de statisticsfeed. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WhoScoredStatisticsFeedResponseDTO(
        @JsonProperty("paging") PagingDTO paging,
        @JsonProperty("playerTableStats") List<PlayerDTO> playerTableStats) {

    /** Accesores internos que mantienen al Adapter independiente del sobre JSON. */
    public Integer currentPage() { return paging == null ? null : paging.currentPage(); }
    public Integer totalPages() { return paging == null ? null : paging.totalPages(); }
    public Integer totalResults() { return paging == null ? null : paging.totalResults(); }
    public Integer resultsPerPage() { return paging == null ? null : paging.resultsPerPage(); }
    public List<PlayerDTO> players() { return playerTableStats; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PagingDTO(
            @JsonProperty("currentPage") Integer currentPage,
            @JsonProperty("totalPages") Integer totalPages,
            @JsonProperty("resultsPerPage") Integer resultsPerPage,
            @JsonProperty("totalResults") Integer totalResults) { }

    /** Campos de identidad y metricas existentes que pueden entregar las familias del feed. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlayerDTO(
            @JsonProperty("playerId") Long playerId,
            @JsonProperty("name") String name,
            @JsonProperty("teamId") Long teamId,
            @JsonProperty("teamName") String teamName,
            @JsonProperty("minsPlayed") Integer minsPlayed,
            @JsonProperty("goal") Integer goal,
            @JsonProperty("assistTotal") Integer assistTotal,
            @JsonProperty("shotsPerGame") BigDecimal shotsPerGame,
            @JsonProperty("keyPassPerGame") BigDecimal keyPassPerGame,
            @JsonProperty("dribbleWonPerGame") BigDecimal dribbleWonPerGame,
            @JsonProperty("tacklePerGame") BigDecimal tacklePerGame,
            @JsonProperty("rating") BigDecimal rating,
            @JsonProperty("yellowCard") Integer yellowCard,
            @JsonProperty("redCard") Integer redCard) { }
}
