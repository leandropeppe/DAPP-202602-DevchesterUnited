package com.devchesterunited.devchesterunitedv2.service.dto.jugador;

import java.math.BigDecimal;
import java.util.UUID;

public record JugadorResultado(UUID id, String nombre, String liga, String equipo, String posicion,
        int dorsal, String nacionalidad, BigDecimal valuation) {
    public JugadorResultado(UUID id, String nombre, String liga, String equipo, String posicion,
            int dorsal, String nacionalidad) {
        this(id, nombre, liga, equipo, posicion, dorsal, nacionalidad, null);
    }
}
