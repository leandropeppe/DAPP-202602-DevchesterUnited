package com.devchesterunited.devchesterunitedv2.modelo;

import java.util.UUID;

/** Identidad de un jugador en un proveedor externo, independiente del UUID local. */
public record IdentidadExternaJugador(
        UUID jugadorId,
        ProveedorIdentidadExterna proveedor,
        long externalId) {
    public IdentidadExternaJugador {
        if (jugadorId == null || proveedor == null || externalId <= 0) {
            throw new IllegalArgumentException("La identidad externa requiere jugador, proveedor e identificador válidos.");
        }
    }
}
