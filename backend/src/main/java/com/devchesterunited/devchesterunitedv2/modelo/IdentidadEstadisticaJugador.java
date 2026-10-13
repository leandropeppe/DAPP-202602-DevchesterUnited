package com.devchesterunited.devchesterunitedv2.modelo;

import java.util.Objects;

/**
 * Hints transitorios para resolver la identidad local de un registro externo.
 *
 * El identificador pertenece al namespace del proveedor y no identifica por sí
 * mismo a un {@code Jugador}; nombre, equipo y atributos opcionales solo sirven
 * como evidencia para el matching inicial y no se persisten como claves.
 */
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
