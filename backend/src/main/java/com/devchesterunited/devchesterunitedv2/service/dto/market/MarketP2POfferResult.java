package com.devchesterunited.devchesterunitedv2.service.dto.market;

import java.util.UUID;

/** Resultado interno de una futura publicación P2P. */
public record MarketP2POfferResult(UUID offerId, UUID sellerId, UUID playerId,
        Quantities quantities, String status) {
    public record Quantities(Long original, Long remaining, Long reserved) { }
}
