package com.devchesterunited.devchesterunitedv2.modelo;

import java.util.UUID;

/** Identidad de un equipo en un proveedor externo, independiente del UUID local. */
public record IdentidadExternaEquipo(
        UUID equipoId,
        ProveedorIdentidadExterna proveedor,
        long externalId) {

    public IdentidadExternaEquipo {
        if (equipoId == null || proveedor == null || externalId <= 0) {
            throw new IllegalArgumentException(
                    "La identidad externa requiere equipo, proveedor e identificador válidos.");
        }
    }
}
