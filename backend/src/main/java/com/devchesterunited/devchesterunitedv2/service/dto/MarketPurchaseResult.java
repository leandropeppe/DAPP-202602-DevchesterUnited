package com.devchesterunited.devchesterunitedv2.service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Internal purchase result; HTTP mapping belongs to a later task. */
public record MarketPurchaseResult(
        UUID solicitudId,
        UUID transaccionId,
        UUID usuarioId,
        UUID jugadorId,
        Long cantidad,
        BigDecimal precioUnitario,
        BigDecimal montoTotal,
        MarketIdempotencyStatus estado,
        Instant actualizadoEn) { }
