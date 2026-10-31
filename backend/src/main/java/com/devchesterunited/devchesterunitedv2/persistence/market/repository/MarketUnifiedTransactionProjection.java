package com.devchesterunited.devchesterunitedv2.persistence.market.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Proyección común de una transacción legacy o de un asiento P2P. */
public interface MarketUnifiedTransactionProjection {
    UUID getTransactionId();
    UUID getUserId();
    UUID getPlayerId();
    String getPlayerNameSnapshot();
    String getOperationType();
    Long getQuantity();
    BigDecimal getUnitPrice();
    BigDecimal getTotalAmount();
    UUID getCounterpartyId();
    UUID getQuoteId();
    Long getQuoteVersion();
    UUID getIdempotencyRequestId();
    BigDecimal getRealizedPnl();
    Instant getConfirmedAt();
}
