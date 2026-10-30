package com.devchesterunited.devchesterunitedv2.controller.market.dto;

import com.devchesterunited.devchesterunitedv2.controller.dto.PaginacionDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2POfferPageResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2POfferView;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

/** Respuesta paginada y pública del listado de ofertas P2P. */
public record MarketP2POfferListResponseDTO(
        @JsonProperty("offers") List<Offer> offers,
        @JsonProperty("pagination") PaginacionDTO pagination) {
    public static final class Offer {
        @JsonProperty("offer_id")
        private final UUID offerId;
        @JsonProperty("player_id")
        private final UUID playerId;
        @JsonProperty("seller_alias")
        private final String sellerAlias;
        @JsonProperty("remaining_quantity")
        private final Long remainingQuantity;
        @JsonProperty("status")
        private final String status;
        @JsonProperty("purchasable")
        private final boolean purchasable;
        @JsonProperty("quote")
        private final Quote quote;

        private Offer(MarketP2POfferView view) {
            this.offerId = view.identity().offerId();
            this.playerId = view.identity().playerId();
            this.sellerAlias = view.identity().sellerAlias();
            this.remainingQuantity = view.availability().remainingQuantity();
            this.status = view.availability().status();
            this.purchasable = view.availability().purchasable();
            var domainQuote = view.quote();
            this.quote = domainQuote == null ? null : new Quote(domainQuote.quoteId(),
                    domainQuote.version(), domainQuote.unitPrice(), domainQuote.publishedAt(),
                    domainQuote.validFrom(), domainQuote.validUntil());
        }

        public UUID getOfferId() { return offerId; }
        public UUID getPlayerId() { return playerId; }
        public String getSellerAlias() { return sellerAlias; }
        public Long getRemainingQuantity() { return remainingQuantity; }
        public String getStatus() { return status; }
        public boolean isPurchasable() { return purchasable; }
        public Quote getQuote() { return quote; }
    }

    public record Quote(@JsonProperty("quote_id") java.util.UUID quoteId,
            @JsonProperty("version") long version,
            @JsonProperty("unit_price") java.math.BigDecimal unitPrice,
            @JsonProperty("published_at") java.time.Instant publishedAt,
            @JsonProperty("valid_from") java.time.Instant validFrom,
            @JsonProperty("valid_until") java.time.Instant validUntil) { }

    public static MarketP2POfferListResponseDTO fromDomain(MarketP2POfferPageResult result) {
        return new MarketP2POfferListResponseDTO(result.offers().stream()
                .map(MarketP2POfferListResponseDTO::toOffer).toList(),
                new PaginacionDTO(result.pagination().paginaActual(), result.pagination().tamanoPagina(),
                        result.pagination().totalPaginas(), result.pagination().totalRegistros()));
    }

    private static Offer toOffer(MarketP2POfferView view) {
        return new Offer(view);
    }
}
