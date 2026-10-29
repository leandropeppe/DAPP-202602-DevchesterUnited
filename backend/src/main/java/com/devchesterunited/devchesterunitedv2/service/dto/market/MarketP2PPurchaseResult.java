package com.devchesterunited.devchesterunitedv2.service.dto.market;

import java.math.BigDecimal;
import java.util.UUID;

/** Resultado interno de una futura compra P2P. */
public record MarketP2PPurchaseResult(UUID tradeId, UUID offerId, Long quantity,
        BigDecimal totalAmount, Long remainingQuantity, String offerStatus) { }
