package com.devchesterunited.devchesterunitedv2.service.dto;

import java.util.Objects;
import java.util.UUID;

/** Exact reference and durable reason supplied by Player Valuation. */
public record QuoteInvalidationRequest(
        UUID playerId,
        UUID quoteId,
        long version,
        String reason) {

    public QuoteInvalidationRequest {
        Objects.requireNonNull(playerId, "El jugador es obligatorio.");
        Objects.requireNonNull(quoteId, "La cotizacion es obligatoria.");
        if (version <= 0) {
            throw new IllegalArgumentException("La version debe ser positiva.");
        }
        if (reason == null || reason.isBlank() || reason.trim().length() > 200) {
            throw new IllegalArgumentException("El motivo de invalidacion es invalido.");
        }
        reason = reason.trim();
    }
}
