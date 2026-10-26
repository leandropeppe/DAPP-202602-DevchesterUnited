package com.devchesterunited.devchesterunitedv2.modelo.estadisticas;

import java.util.List;
import java.util.Objects;

/** Snapshot completo y normalizado de una competicion. */
public record SnapshotEstadisticasJugador(
        CompeticionEstadistica competicion,
        List<RegistroEstadisticasJugador> registros) {

    public SnapshotEstadisticasJugador {
        Objects.requireNonNull(competicion, "La competicion es obligatoria.");
        List<RegistroEstadisticasJugador> registrosNoNulos = Objects.requireNonNull(registros,
                "Los registros son obligatorios.");
        if (registrosNoNulos.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("El snapshot no puede contener registros nulos.");
        }
        registros = List.copyOf(registrosNoNulos);
    }
}
