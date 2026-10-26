package com.devchesterunited.devchesterunitedv2.service.event;

import java.util.UUID;

/** Publicado cuando el catálogo confirma que un jugador quedó activo. */
public record JugadorActivadoEvent(UUID jugadorId) {
    public JugadorActivadoEvent {
        if (jugadorId == null) {
            throw new IllegalArgumentException("El jugador es obligatorio.");
        }
    }
}
