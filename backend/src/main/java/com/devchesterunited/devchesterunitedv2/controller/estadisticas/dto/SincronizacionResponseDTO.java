package com.devchesterunited.devchesterunitedv2.controller.estadisticas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SincronizacionResponseDTO(
        @JsonProperty("teams_processed") int equiposProcesados,
        @JsonProperty("players_processed") int jugadoresProcesados,
        @JsonProperty("teams_created") int equiposCreados,
        @JsonProperty("teams_updated") int equiposActualizados,
        @JsonProperty("players_created") int jugadoresCreados,
        @JsonProperty("players_updated") int jugadoresActualizados) { }
