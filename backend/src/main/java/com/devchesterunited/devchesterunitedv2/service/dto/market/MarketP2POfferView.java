package com.devchesterunited.devchesterunitedv2.service.dto.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Vista publica de una oferta, separando estado persistido y disponibilidad derivada. */
public record MarketP2POfferView(Identity identity, Availability availability, Quote quote) {
    public record Identity(UUID offerId, UUID playerId, String playerName, String sellerAlias,
            boolean mine) {
        public Identity(UUID offerId, UUID playerId, String sellerAlias, boolean mine) {
            this(offerId, playerId, null, sellerAlias, mine);
        }

        public Identity(UUID offerId, UUID playerId, String sellerAlias) {
            this(offerId, playerId, null, sellerAlias, false);
        }
    }

    public record Availability(Long remainingQuantity, String status, boolean purchasable) { }

    public record Quote(UUID quoteId, long version, BigDecimal unitPrice,
            Instant publishedAt, Instant validFrom, Instant validUntil) { }
}
