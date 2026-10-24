package com.devchesterunited.devchesterunitedv2.service.dto.jugador;

import java.math.BigDecimal;
import java.util.UUID;

public record JugadorRankingResultado(int rank, UUID playerId, String nombre, String posicion,
        String equipo, BigDecimal cotizacion, String strategyId, int strategyVersion) { }
