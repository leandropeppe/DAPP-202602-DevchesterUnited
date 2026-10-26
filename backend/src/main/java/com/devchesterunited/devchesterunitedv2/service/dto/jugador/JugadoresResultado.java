package com.devchesterunited.devchesterunitedv2.service.dto.jugador;
import com.devchesterunited.devchesterunitedv2.service.dto.shared.PaginacionResultado;

import java.util.List;

public record JugadoresResultado(List<JugadorResultado> jugadores, PaginacionResultado paginacion) {
    public JugadoresResultado {
        jugadores = List.copyOf(jugadores);
    }
}
