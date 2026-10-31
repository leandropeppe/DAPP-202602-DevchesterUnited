package com.devchesterunited.devchesterunitedv2.service.dto.market;

import java.util.UUID;

/** Resultado interno de una futura cancelación P2P. */
public record MarketP2PCancelResult(UUID offerId, Long originalQuantity,
        Long soldQuantity, Long releasedQuantity, String status) { }
