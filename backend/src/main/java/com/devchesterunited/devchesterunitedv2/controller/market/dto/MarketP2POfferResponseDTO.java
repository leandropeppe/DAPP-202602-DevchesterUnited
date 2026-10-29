package com.devchesterunited.devchesterunitedv2.controller.market.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2POfferResult;

import java.util.UUID;

/** Respuesta pública de una oferta P2P sin información sensible del vendedor. */
public record MarketP2POfferResponseDTO(
        @JsonProperty("offer_id") UUID ofertaId,
        @JsonProperty("player_id") UUID jugadorId,
        @JsonProperty("original_quantity") Long cantidadOriginal,
        @JsonProperty("remaining_quantity") Long cantidadRestante,
        @JsonProperty("reserved_quantity") Long cantidadReservada,
        @JsonProperty("status") String estado) {

    public static MarketP2POfferResponseDTO fromDomain(MarketP2POfferResult result) {
        var quantities = result.quantities();
        return new MarketP2POfferResponseDTO(result.offerId(), result.playerId(),
                quantities == null ? null : quantities.original(),
                quantities == null ? null : quantities.remaining(),
                quantities == null ? null : quantities.reserved(), result.status());
    }
}
