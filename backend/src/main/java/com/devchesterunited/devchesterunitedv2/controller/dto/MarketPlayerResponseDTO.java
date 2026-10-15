package com.devchesterunited.devchesterunitedv2.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

public record MarketPlayerResponseDTO(
        String id,
        String name,
        String league,
        String team,
        @JsonProperty("player_active") boolean playerActive,
        @JsonProperty("team_active") boolean teamActive,
        @JsonProperty("tokens_available") long tokensAvailable,
        boolean operable,
        @JsonProperty("quote_id") String quoteId,
        @JsonProperty("quote_version") Long quoteVersion,
        @JsonProperty("price_per_token") BigDecimal pricePerToken,
        @JsonProperty("quote_status") String quoteStatus,
        @JsonProperty("quote_availability") String quoteAvailability,
        @JsonProperty("valid_from") Instant validFrom,
        @JsonProperty("valid_until") Instant validUntil) { }
