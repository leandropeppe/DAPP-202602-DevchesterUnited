package com.devchesterunited.devchesterunitedv2.service.dto.jugador;

import com.devchesterunited.devchesterunitedv2.service.dto.estadisticas.EstadisticasJugadorResultado;

import java.math.BigDecimal;
import java.util.UUID;

public record JugadorDetalleResultado(
        UUID id,
        String nombre,
        String liga,
        String equipo,
        String posicion,
        int dorsal,
        String nacionalidad,
        EstadisticasJugadorResultado estadisticas,
        BigDecimal valuation) { }
