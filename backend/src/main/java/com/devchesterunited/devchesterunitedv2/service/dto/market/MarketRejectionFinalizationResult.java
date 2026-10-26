package com.devchesterunited.devchesterunitedv2.service.dto.market;

/** Resultado interno de la transición coordinada de un rechazo. */
public record MarketRejectionFinalizationResult(
        MarketIdempotencyResult resultado,
        boolean transicionRealizada) { }
