package com.devchesterunited.devchesterunitedv2.persistence.market.projection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Proyección única para listar ofertas y su cotización vigente. */
public interface MarketP2POfferProjection {
    UUID getOfferId();
    UUID getPlayerId();
    String getPlayerName();
    UUID getSellerId();
    String getSellerAlias();
    Long getRemainingQuantity();
    String getPersistedStatus();
    boolean getPlayerActive();
    String getHeaderValidityStatus();
    UUID getQuoteId();
    Long getQuoteVersion();
    BigDecimal getUnitPrice();
    Instant getPublishedAt();
    Instant getValidFrom();
    Instant getValidUntil();
    String getQuoteValidityStatus();
    String getStrategyReference();
}
