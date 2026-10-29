package com.devchesterunited.devchesterunitedv2.service.dto.market;

import java.util.UUID;

/** Parametros funcionales que identifican exclusivamente una compra P2P. */
public record MarketP2PPurchaseIdentity(UUID offerId, UUID playerId, Long quantity,
        MarketP2PQuoteIdentity quote) {
    public void validar() {
        if (offerId == null || playerId == null || quantity == null || quantity <= 0
                || quote == null) {
            throw new IllegalArgumentException("La identidad de compra P2P no es valida.");
        }
        quote.validar();
    }
}
