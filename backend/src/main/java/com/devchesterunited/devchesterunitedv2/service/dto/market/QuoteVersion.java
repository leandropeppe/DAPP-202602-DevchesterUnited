package com.devchesterunited.devchesterunitedv2.service.dto.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Versión publicada e inmutable de una cotización de un jugador. */
public record QuoteVersion(
        UUID quoteId,
        long version,
        UUID playerId,
        BigDecimal pricePerToken,
        Instant publishedAt,
        Instant validFrom,
        Instant validUntil,
        QuoteValidityStatus validityStatus,
        String strategyReference) {

    public QuoteVersion {
        Objects.requireNonNull(quoteId, "El identificador de cotización es obligatorio.");
        Objects.requireNonNull(playerId, "El jugador de la cotización es obligatorio.");
        Objects.requireNonNull(pricePerToken, "El precio de la cotización es obligatorio.");
        Objects.requireNonNull(publishedAt, "La fecha de publicación es obligatoria.");
        Objects.requireNonNull(validFrom, "El inicio de vigencia es obligatorio.");
        Objects.requireNonNull(validityStatus, "El estado de vigencia es obligatorio.");
        if (version <= 0) {
            throw new IllegalArgumentException("La versión de cotización debe ser positiva.");
        }
        if (pricePerToken.signum() <= 0) {
            throw new IllegalArgumentException("El precio por token debe ser positivo.");
        }
        if (pricePerToken.scale() > 2) {
            throw new IllegalArgumentException("El precio por token no puede superar dos decimales.");
        }
        if (publishedAt.isAfter(validFrom)) {
            throw new IllegalArgumentException("La publicación no puede ocurrir después del inicio de vigencia.");
        }
        if (validUntil != null && validUntil.isBefore(validFrom)) {
            throw new IllegalArgumentException("El fin de vigencia no puede preceder a su inicio.");
        }
        pricePerToken = pricePerToken.setScale(2);
        strategyReference = normalizeStrategyReference(strategyReference);
    }

    public boolean isValidAt(Instant instant) {
        Objects.requireNonNull(instant, "El instante de validación es obligatorio.");
        return validityStatus == QuoteValidityStatus.VALID
                && !instant.isBefore(validFrom)
                && (validUntil == null || instant.isBefore(validUntil));
    }

    private static String normalizeStrategyReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        return reference.trim();
    }
}
