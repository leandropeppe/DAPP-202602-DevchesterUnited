package com.devchesterunited.devchesterunitedv2.service.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Durable public result of an exact quote invalidation. */
public record QuoteInvalidationResult(
        UUID playerId,
        UUID quoteId,
        long version,
        QuoteValidityStatus status,
        Instant invalidatedAt,
        String reason) {

    public QuoteInvalidationResult {
        Objects.requireNonNull(playerId, "El jugador es obligatorio.");
        Objects.requireNonNull(quoteId, "La cotizacion es obligatoria.");
        Objects.requireNonNull(status, "El estado es obligatorio.");
        Objects.requireNonNull(invalidatedAt, "La fecha de invalidacion es obligatoria.");
        if (version <= 0 || status != QuoteValidityStatus.INVALIDATED
                || reason == null || reason.isBlank() || reason.trim().length() > 200) {
            throw new IllegalArgumentException("El resultado de invalidacion es invalido.");
        }
        reason = reason.trim();
    }
}
