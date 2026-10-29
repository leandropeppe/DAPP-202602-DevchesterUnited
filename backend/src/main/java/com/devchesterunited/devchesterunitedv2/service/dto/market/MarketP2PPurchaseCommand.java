package com.devchesterunited.devchesterunitedv2.service.dto.market;

import java.util.UUID;

/** Comando interno para una futura compra P2P. */
public record MarketP2PPurchaseCommand(UUID usuarioId, UUID offerId, Long cantidad,
        MarketObservedQuote quote, String claveIdempotencia) { }
