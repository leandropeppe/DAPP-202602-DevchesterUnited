package com.devchesterunited.devchesterunitedv2.modelo;

import java.util.Objects;

/** Hints transitorios para resolver la identidad local de un registro externo. */
public record IdentidadEstadisticaJugador(
        long playerId,
        String nombre,
        String posicion,
        String nacionalidad,
        Long teamId,
        String nombreEquipo) {

    public IdentidadEstadisticaJugador {
        if (playerId <= 0) {
            throw new IllegalArgumentException("El identificador externo del jugador debe ser positivo.");
        }
        Objects.requireNonNull(nombre, "El nombre externo del jugador es obligatorio.");
        if (nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre externo del jugador no puede estar vacio.");
        }
        if (teamId != null && teamId <= 0) {
            throw new IllegalArgumentException("El identificador externo del equipo debe ser positivo.");
        }
    }
}
