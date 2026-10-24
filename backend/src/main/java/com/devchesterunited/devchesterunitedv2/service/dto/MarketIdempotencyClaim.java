package com.devchesterunited.devchesterunitedv2.service.dto;

/**
 * Resultado de una reclamación junto con la propiedad de ejecución.
 * Un estado PROCESSING, por sí solo, nunca autoriza a ejecutar la operación.
 */
public record MarketIdempotencyClaim(
        MarketIdempotencyResult resultado,
        boolean propietarioEjecucion) { }
