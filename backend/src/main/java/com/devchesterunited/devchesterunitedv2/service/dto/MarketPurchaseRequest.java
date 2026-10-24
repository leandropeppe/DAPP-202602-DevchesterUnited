package com.devchesterunited.devchesterunitedv2.service.dto;

import java.util.UUID;

/** Internal purchase command; the controller supplies the authenticated user id. */
public record MarketPurchaseRequest(
        UUID usuarioId,
        String claveIdempotencia,
        UUID jugadorId,
        Long cantidad,
        UUID cotizacionId,
        Long versionCotizacion) { }
