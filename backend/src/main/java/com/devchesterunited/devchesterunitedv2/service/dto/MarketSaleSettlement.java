package com.devchesterunited.devchesterunitedv2.service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Validated sale data handed to the atomic persistence boundary by T053.
 * P/L, holdings and movements are deliberately not calculated here.
 */
public record MarketSaleSettlement(
        UUID solicitudId,
        UUID usuarioId,
        UUID jugadorId,
        String nombreJugadorSnapshot,
        UUID contraparteId,
        UUID quoteId,
        Long versionCotizacion,
        Long cantidad,
        BigDecimal precioUnitario,
        BigDecimal montoTotal,
        Instant confirmadoEn) { }
