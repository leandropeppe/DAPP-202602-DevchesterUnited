package com.devchesterunited.devchesterunitedv2.modelo.equipo;

import java.time.LocalDate;

/** Temporada actual informada para un equipo. No representa historico. */
public record TemporadaEquipo(Long temporadaExternaId, LocalDate inicio, LocalDate fin) {

    public TemporadaEquipo {
        if (temporadaExternaId != null && temporadaExternaId <= 0) {
            throw new IllegalArgumentException("La temporada externa debe ser positiva.");
        }
        if ((inicio == null) != (fin == null)
                || inicio != null && fin.isBefore(inicio)) {
            throw new IllegalArgumentException("Las fechas de temporada son incoherentes.");
        }
    }
}
