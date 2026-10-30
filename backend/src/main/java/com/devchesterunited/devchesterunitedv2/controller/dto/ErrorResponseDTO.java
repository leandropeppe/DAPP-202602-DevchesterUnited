package com.devchesterunited.devchesterunitedv2.controller.dto;

import com.devchesterunited.devchesterunitedv2.controller.market.dto.MarketP2PQuoteResponseDTO;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ErrorResponseDTO(String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<DetalleErrorDTO> errors,
        @JsonInclude(JsonInclude.Include.NON_NULL) String code,
        @JsonProperty("current_quote")
        @JsonInclude(JsonInclude.Include.NON_NULL) MarketP2PQuoteResponseDTO currentQuote) {
    public ErrorResponseDTO(String message) { this(message, null); }

    public ErrorResponseDTO(String message, List<DetalleErrorDTO> errors) {
        this(message, errors, null, null);
    }

    public ErrorResponseDTO(String message, String code,
            MarketP2PQuoteResponseDTO currentQuote) {
        this(message, null, code, currentQuote);
    }

    @JsonProperty("status")
    public String status() {
        return "error";
    }

    public record DetalleErrorDTO(String field, String issue) { }
}
