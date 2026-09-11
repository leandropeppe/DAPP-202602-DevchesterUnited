package com.devchesterunited.devchesterunitedv2.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Contenido de data; se puede envolver con SuccessResponseDTO sin mensaje. */
public record JugadoresResponseDTO(
        @JsonProperty("players") List<JugadorResponseDTO> jugadores,
        @JsonProperty("pagination") PaginacionDTO paginacion) {
    public JugadoresResponseDTO {
        jugadores = List.copyOf(jugadores);
    }
}
