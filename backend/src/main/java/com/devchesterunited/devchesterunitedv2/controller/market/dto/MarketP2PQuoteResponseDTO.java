package com.devchesterunited.devchesterunitedv2.controller.market.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteVersion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Public quote snapshot returned when the observed quote changed. */
public record MarketP2PQuoteResponseDTO(
        @JsonProperty("quote_id") UUID quoteId,
        @JsonProperty("quote_version") Long version,
        @JsonProperty("player_id") UUID playerId,
        @JsonProperty("unit_price") BigDecimal unitPrice,
        @JsonProperty("valid_from") Instant validFrom,
        @JsonProperty("valid_until") Instant validUntil) {

    public static MarketP2PQuoteResponseDTO fromDomain(QuoteVersion quote) {
        return quote == null ? null : new MarketP2PQuoteResponseDTO(quote.quoteId(),
                quote.version(), quote.playerId(), quote.pricePerToken(), quote.validFrom(),
                quote.validUntil());
    }
}
