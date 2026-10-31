package com.devchesterunited.devchesterunitedv2.service.dto.market;

import java.util.UUID;

/** Comando interno para comprar una oferta P2P. */
public record MarketP2PPurchaseCommand(UUID usuarioId, String claveIdempotencia,
        UUID offerId, UUID playerId, Long quantity, MarketP2PQuoteIdentity quote) {
    public MarketP2PPurchaseIdentity idempotencyIdentity() {
        return new MarketP2PPurchaseIdentity(offerId, playerId, quantity, quote);
    }

    public void validar() {
        if (usuarioId == null || claveIdempotencia == null || claveIdempotencia.isBlank()
                || claveIdempotencia.trim().length() > 200) {
            throw new IllegalArgumentException("El comando de compra P2P no es valido.");
        }
        idempotencyIdentity().validar();
    }
}
