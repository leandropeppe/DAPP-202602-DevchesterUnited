package com.devchesterunited.devchesterunitedv2.service.dto.market;

import java.time.Instant;
import java.util.UUID;

/** Resultado interno; el execution token nunca debe mapearse a DTOs publicos. */
public record MarketIdempotencyResult(
        UUID solicitudId,
        UUID usuarioId,
        String claveIdempotencia,
        String tipoOperacion,
        String hashParametros,
        MarketIdempotencyStatus estado,
        UUID tokenEjecucion,
        UUID referenciaResultado,
        Instant actualizadoEn) { }
