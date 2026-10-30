package com.devchesterunited.devchesterunitedv2.controller.market.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** HTTP request for a P2P purchase; identity comes from JWT and the path. */
public final class MarketP2PPurchaseRequestDTO {
    @NotNull
    @Positive
    @JsonProperty("quantity")
    private Long quantity;

    @NotNull
    @JsonProperty("quote_id")
    private UUID quoteId;

    @NotNull
    @Positive
    @JsonProperty("quote_version")
    private Long quoteVersion;

    private final Set<String> unknownFields = new HashSet<>();

    @JsonAnySetter
    public void registerUnknownField(String name, JsonNode value) {
        unknownFields.add(name);
    }

    public void validateFields() {
        if (!unknownFields.isEmpty()) {
            throw new TokenMarketException(TokenMarketException.Reason.INVALID_INPUT,
                    "La solicitud contiene campos no permitidos.");
        }
    }

    public Long getQuantity() { return quantity; }
    public UUID getQuoteId() { return quoteId; }
    public Long getQuoteVersion() { return quoteVersion; }
}
