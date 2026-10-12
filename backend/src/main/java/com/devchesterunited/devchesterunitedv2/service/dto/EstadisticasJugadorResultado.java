package com.devchesterunited.devchesterunitedv2.service.dto;

import java.math.BigDecimal;

public record EstadisticasJugadorResultado(
        String posicion,
        Integer minutosJugados,
        Integer goles,
        Integer asistencias,
        BigDecimal tiros,
        BigDecimal pasesClave,
        BigDecimal regates,
        BigDecimal tackles,
        BigDecimal valoracion,
        Integer tarjetasAmarillas,
        Integer tarjetasRojas) { }
