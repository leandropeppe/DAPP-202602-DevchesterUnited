package com.devchesterunited.devchesterunitedv2.service.dto;

import java.util.UUID;

public record JugadorDetalleResultado(
        UUID id,
        String nombre,
        String liga,
        String equipo,
        String posicion,
        int dorsal,
        String nacionalidad,
        EstadisticasJugadorResultado estadisticas) { }
