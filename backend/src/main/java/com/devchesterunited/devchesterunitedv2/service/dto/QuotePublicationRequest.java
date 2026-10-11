package com.devchesterunited.devchesterunitedv2.service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Datos inmutables necesarios para publicar una nueva versión de cotización. */
public record QuotePublicationRequest(
        UUID playerId,
        BigDecimal pricePerToken,
        Instant publishedAt,
        Instant validFrom,
        Instant validUntil,
        String strategyReference) {

    public QuotePublicationRequest {
        Objects.requireNonNull(playerId, "El jugador de la cotización es obligatorio.");
        Objects.requireNonNull(pricePerToken, "El precio de la cotización es obligatorio.");
        Objects.requireNonNull(publishedAt, "La fecha de publicación es obligatoria.");
        Objects.requireNonNull(validFrom, "El inicio de vigencia es obligatorio.");
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

    public static QuotePublicationRequest initial(
            UUID playerId, Instant publishedAt) {
        return new QuotePublicationRequest(
                playerId,
                new BigDecimal("1.00"),
                publishedAt,
                publishedAt,
                null,
                null);
    }

    private static String normalizeStrategyReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        return reference.trim();
    }
}
