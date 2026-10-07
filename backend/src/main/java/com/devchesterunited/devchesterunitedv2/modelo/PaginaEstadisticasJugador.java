package com.devchesterunited.devchesterunitedv2.modelo;

import java.util.List;
import java.util.Objects;

/** Página normalizada con metadata suficiente para validar completitud. */
public record PaginaEstadisticasJugador(
        CompeticionEstadistica competicion,
        FamiliaEstadistica familia,
        int paginaActual,
        int tamanoPagina,
        int totalPaginas,
        int totalResultados,
        List<RegistroEstadisticasJugador> registros) {

    public PaginaEstadisticasJugador {
        Objects.requireNonNull(competicion, "La competición es obligatoria.");
        Objects.requireNonNull(familia, "La familia es obligatoria.");
        registros = List.copyOf(Objects.requireNonNull(registros, "Los registros son obligatorios."));
        if (paginaActual < 1 || tamanoPagina < 1 || totalPaginas < 1 || totalResultados < 0) {
            throw new IllegalArgumentException("La metadata de paginación no es válida.");
        }
        if (paginaActual > totalPaginas || registros.size() > tamanoPagina) {
            throw new IllegalArgumentException("La página no es consistente con su metadata.");
        }
        if (registros.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("La página no puede contener registros nulos.");
        }
    }
}
