package com.devchesterunited.devchesterunitedv2.service.dto;

import java.util.UUID;

/** Internal sale command; the controller will supply the authenticated user id. */
public record MarketSaleRequest(
        UUID usuarioId,
        String claveIdempotencia,
        UUID jugadorId,
        Long cantidad,
        UUID cotizacionId,
        Long versionCotizacion) { }
