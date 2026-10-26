package com.devchesterunited.devchesterunitedv2.controller.market.dto;
import com.devchesterunited.devchesterunitedv2.controller.dto.PaginacionDTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record MarketPlayersResponseDTO(
        @JsonProperty("players") List<MarketPlayerResponseDTO> jugadores,
        @JsonProperty("pagination") PaginacionDTO paginacion) {
    public MarketPlayersResponseDTO {
        jugadores = List.copyOf(jugadores);
    }
}
