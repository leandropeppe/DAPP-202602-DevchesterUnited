package com.devchesterunited.devchesterunitedv2.modelo;

import java.util.UUID;

/** Identidad de un jugador en un proveedor externo, independiente del UUID local. */
public final class IdentidadExternaJugador {
    private final UUID jugadorId;
    private final ProveedorIdentidadExterna proveedor;
    private final long externalId;

    public IdentidadExternaJugador(UUID jugadorId, ProveedorIdentidadExterna proveedor, long externalId) {
        if (jugadorId == null || proveedor == null || externalId <= 0) {
            throw new IllegalArgumentException("La identidad externa requiere jugador, proveedor e identificador válidos.");
        }
        this.jugadorId = jugadorId;
        this.proveedor = proveedor;
        this.externalId = externalId;
    }

    public UUID getJugadorId() { return jugadorId; }
    public ProveedorIdentidadExterna getProveedor() { return proveedor; }
    public long getExternalId() { return externalId; }
}
