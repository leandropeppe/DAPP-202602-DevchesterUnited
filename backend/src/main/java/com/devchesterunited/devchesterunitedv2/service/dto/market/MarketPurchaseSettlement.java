package com.devchesterunited.devchesterunitedv2.service.dto.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Validated purchase data handed to the atomic persistence boundary by T046. */
public record MarketPurchaseSettlement(
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
