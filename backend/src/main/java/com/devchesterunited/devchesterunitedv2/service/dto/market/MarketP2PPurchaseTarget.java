package com.devchesterunited.devchesterunitedv2.service.dto.market;

import java.util.UUID;

/** Referencias inmutables del jugador y la oferta que se liquidan. */
public record MarketP2PPurchaseTarget(UUID offerId, UUID playerId,
        String playerName) { }
