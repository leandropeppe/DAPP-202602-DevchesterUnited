package com.devchesterunited.devchesterunitedv2.controller.jugador.dto;

import java.util.List;

public record RankingJugadoresResponseDTO(List<JugadorRankingResponseDTO> players) {
    public RankingJugadoresResponseDTO {
        players = List.copyOf(players);
    }
}
