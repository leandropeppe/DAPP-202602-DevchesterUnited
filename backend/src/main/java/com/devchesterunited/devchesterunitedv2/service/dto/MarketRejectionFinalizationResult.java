package com.devchesterunited.devchesterunitedv2.service.dto;

/** Resultado interno de la transición coordinada de un rechazo. */
public record MarketRejectionFinalizationResult(
        MarketIdempotencyResult resultado,
        boolean transicionRealizada) { }
