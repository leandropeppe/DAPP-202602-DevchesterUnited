package com.devchesterunited.devchesterunitedv2.controller.market.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Error envelope for P2P quote availability, including an explicit null quote. */
public record MarketP2PErrorResponseDTO(
        String message,
        String code,
        @JsonProperty("current_quote")
        @JsonInclude(JsonInclude.Include.ALWAYS) MarketP2PQuoteResponseDTO currentQuote,
        @JsonProperty("offer_status") String offerStatus) {

    @JsonProperty("status")
    public String status() {
        return "error";
    }
}
