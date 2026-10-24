package com.devchesterunited.devchesterunitedv2.service.dto;

import java.util.UUID;

/** Datos normalizados que identifican una ejecucion idempotente interna. */
public record MarketIdempotencyRequest(
        UUID usuarioId,
        String claveIdempotencia,
        String tipoOperacion,
        UUID jugadorId,
        Long cantidad,
        UUID cotizacionId,
        Long versionCotizacion) { }
