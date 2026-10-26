package com.devchesterunited.devchesterunitedv2.persistence.market.projection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Proyección agregada de lectura del catálogo y del estado operativo de mercado. */
public interface MarketPlayerAvailabilityProjection {
    UUID getPlayerId();
    String getPlayerName();
    String getLeague();
    String getTeamName();
    boolean getPlayerActive();
    boolean getTeamActive();
    Long getIssuedQuantity();
    Long getInventoryQuantity();
    UUID getCurrentQuoteId();
    Long getCurrentQuoteVersion();
    BigDecimal getCurrentPricePerToken();
    String getCurrentStrategyReference();
    String getCurrentValidityStatus();
    Instant getCurrentValidFrom();
    Instant getCurrentValidUntil();
    UUID getLatestQuoteId();
    Long getLatestQuoteVersion();
    BigDecimal getLatestPricePerToken();
    String getLatestValidityStatus();
    Instant getLatestValidFrom();
    Instant getLatestValidUntil();
}
