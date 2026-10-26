package com.devchesterunited.devchesterunitedv2.service.dto.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Internal sale result; HTTP mapping belongs to a later task. */
public record MarketSaleResult(
        UUID solicitudId,
        UUID transaccionId,
        UUID usuarioId,
        UUID jugadorId,
        Long cantidad,
        BigDecimal precioUnitario,
        BigDecimal montoTotal,
        MarketIdempotencyStatus estado,
        Instant actualizadoEn) { }
