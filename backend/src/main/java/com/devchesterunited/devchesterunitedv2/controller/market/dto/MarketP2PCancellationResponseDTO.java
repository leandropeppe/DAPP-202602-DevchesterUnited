package com.devchesterunited.devchesterunitedv2.controller.market.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PCancelResult;

import java.util.UUID;

/** Public cancellation result containing the immutable quantity audit. */
public record MarketP2PCancellationResponseDTO(
        @JsonProperty("offer_id") UUID offerId,
        @JsonProperty("original_quantity") Long originalQuantity,
        @JsonProperty("sold_quantity") Long soldQuantity,
        @JsonProperty("released_quantity") Long releasedQuantity,
        @JsonProperty("status") String status) {

    public static MarketP2PCancellationResponseDTO fromDomain(MarketP2PCancelResult result) {
        return new MarketP2PCancellationResponseDTO(result.offerId(), result.originalQuantity(),
                result.soldQuantity(), result.releasedQuantity(), result.status());
    }
}
