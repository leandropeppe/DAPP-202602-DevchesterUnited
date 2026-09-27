package com.devchesterunited.devchesterunitedv2.service.dto;

import java.util.List;

public record JugadoresResultado(List<JugadorResultado> jugadores, PaginacionResultado paginacion) {
    public JugadoresResultado {
        jugadores = List.copyOf(jugadores);
    }
}
