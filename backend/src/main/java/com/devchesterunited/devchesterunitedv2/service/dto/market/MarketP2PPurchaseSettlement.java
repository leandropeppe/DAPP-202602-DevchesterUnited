package com.devchesterunited.devchesterunitedv2.service.dto.market;

import com.devchesterunited.devchesterunitedv2.modelo.market.QuoteVersion;

import java.time.Instant;
import java.util.UUID;

/** Datos ya validados que cruzan al núcleo atómico de liquidación P2P. */
public record MarketP2PPurchaseSettlement(UUID requestId, UUID buyerId,
        MarketP2PPurchaseTarget target, QuoteVersion quote, Long quantity,
        Instant confirmedAt) { }
