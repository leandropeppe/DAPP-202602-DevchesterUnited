package com.devchesterunited.devchesterunitedv2.service.dto.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MarketPlayerAvailability(
        UUID playerId,
        String playerName,
        String league,
        String teamName,
        boolean playerActive,
        boolean teamActive,
        long issuedQuantity,
        long inventoryQuantity,
        UUID quoteId,
        Long quoteVersion,
        BigDecimal pricePerToken,
        String validityStatus,
        Instant validFrom,
        Instant validUntil,
        MarketQuoteAvailability quoteAvailability,
        boolean operable) {

    public boolean hasIssuance() {
        return issuedQuantity > 0;
    }

    public boolean hasInventory() {
        return inventoryQuantity > 0;
    }
}
