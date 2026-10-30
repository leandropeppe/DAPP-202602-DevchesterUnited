package com.devchesterunited.devchesterunitedv2.controller.market.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseResult;

import java.math.BigDecimal;
import java.util.UUID;

/** Public response for a P2P purchase. */
public record MarketP2PPurchaseResponseDTO(
        @JsonUnwrapped Identity identity,
        @JsonUnwrapped Pricing pricing,
        @JsonUnwrapped Quantities quantities,
        @JsonProperty("status") String status,
        @JsonProperty("operation_status")
        @JsonInclude(JsonInclude.Include.NON_NULL) String operationStatus) {

    public static MarketP2PPurchaseResponseDTO fromDomain(MarketP2PPurchaseResult result) {
        var settlement = result.settlement();
        var quote = settlement == null ? null : settlement.quote();
        var resultQuantities = settlement == null ? null : settlement.quantities();
        return new MarketP2PPurchaseResponseDTO(
                new Identity(result.solicitudId(), result.tradeId(), result.offerId(),
                        settlement == null ? null : settlement.jugadorId()),
                new Pricing(settlement == null ? null : settlement.quantity(),
                        quote == null ? null : quote.quoteId(),
                        quote == null ? null : quote.version(),
                        quote == null ? null : quote.unitPrice(),
                        settlement == null ? null : settlement.totalAmount()),
                new Quantities(resultQuantities == null ? null : resultQuantities.remaining(),
                        resultQuantities == null ? null : resultQuantities.reserved()),
                resultQuantities == null ? null : resultQuantities.offerStatus(),
                result.estado() == null ? null : result.estado().name());
    }

    public record Identity(
            @JsonProperty("operation_id") UUID operationId,
            @JsonProperty("trade_id") UUID tradeId,
            @JsonProperty("offer_id") UUID offerId,
            @JsonProperty("player_id") UUID playerId) { }

    public record Pricing(
            @JsonProperty("quantity") Long quantity,
            @JsonProperty("quote_id") UUID quoteId,
            @JsonProperty("quote_version") Long quoteVersion,
            @JsonProperty("unit_price") BigDecimal unitPrice,
            @JsonProperty("total_amount") BigDecimal totalAmount) { }

    public record Quantities(
            @JsonProperty("remaining_quantity") Long remainingQuantity,
            @JsonProperty("reserved_quantity") Long reservedQuantity) { }
}
