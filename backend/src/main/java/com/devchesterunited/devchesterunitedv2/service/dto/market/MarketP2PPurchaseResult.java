package com.devchesterunited.devchesterunitedv2.service.dto.market;

import java.math.BigDecimal;
import java.util.UUID;

/** Resultado interno de una compra P2P; no ejecuta liquidacion por si mismo. */
public record MarketP2PPurchaseResult(UUID solicitudId, UUID tradeId, UUID offerId,
        UUID compradorId, Settlement settlement, MarketIdempotencyStatus estado) {
    public record Quote(UUID quoteId, Long version, BigDecimal unitPrice) { }

    public record Quantities(Long purchased, Long remaining, Long reserved,
            String offerStatus) {
        public Quantities(Long purchased, Long remaining, Long reserved) {
            this(purchased, remaining, reserved, null);
        }
    }

    public record Settlement(UUID vendedorId, UUID jugadorId, Long quantity, Quote quote,
            Quantities quantities, BigDecimal totalAmount) { }
}
