package com.devchesterunited.devchesterunitedv2.persistence.market.mapper;

import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.OfertaVentaP2PEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.projection.MarketP2POfferProjection;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2POfferView;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteValidityStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteVersion;

import java.time.Instant;
import java.util.UUID;

/** Mapea ofertas a una vista pública sin filtrar datos privados del vendedor. */
public final class MarketP2POfferViewMapper {
    private MarketP2POfferViewMapper() { }

    public static MarketP2POfferView toView(OfertaVentaP2PEntity offer,
            JugadorEntity player, String sellerAlias, QuoteVersion quote,
            Instant now, UUID viewerId) {
        boolean operational = player.isActivo() && quote != null && quote.isValidAt(now);
        boolean active = OfertaVentaP2PEntity.OPEN.equals(offer.getEstado())
                || OfertaVentaP2PEntity.PARTIALLY_FILLED.equals(offer.getEstado());
        boolean purchasable = operational && active && !viewerId.equals(offer.getVendedorId());
        String derivedStatus = active ? (operational ? "PURCHASABLE" : "SUSPENDED")
                : offer.getEstado();
        MarketP2POfferView.Quote quoteView = quote == null ? null : new MarketP2POfferView.Quote(
                quote.quoteId(), quote.version(), quote.pricePerToken(), quote.publishedAt(),
                quote.validFrom(), quote.validUntil());
        return new MarketP2POfferView(
                new MarketP2POfferView.Identity(offer.getId(), offer.getJugadorId(), player.getNombre(), sellerAlias,
                        viewerId.equals(offer.getVendedorId())),
                new MarketP2POfferView.Availability(offer.getCantidadRestante(), derivedStatus,
                        purchasable), quoteView);
    }

    public static MarketP2POfferView toView(MarketP2POfferProjection offer,
            Instant now, UUID viewerId) {
        QuoteVersion quote = quoteFrom(offer);
        boolean operational = offer.getPlayerActive()
                && "VALID".equals(offer.getHeaderValidityStatus())
                && quote != null && quote.isValidAt(now);
        boolean active = OfertaVentaP2PEntity.OPEN.equals(offer.getPersistedStatus())
                || OfertaVentaP2PEntity.PARTIALLY_FILLED.equals(offer.getPersistedStatus());
        boolean purchasable = operational && active && !viewerId.equals(offer.getSellerId());
        String status = active ? (operational ? "PURCHASABLE" : "SUSPENDED")
                : offer.getPersistedStatus();
        MarketP2POfferView.Quote quoteView = quote == null ? null : new MarketP2POfferView.Quote(
                quote.quoteId(), quote.version(), quote.pricePerToken(), quote.publishedAt(),
                quote.validFrom(), quote.validUntil());
        return new MarketP2POfferView(
                new MarketP2POfferView.Identity(offer.getOfferId(), offer.getPlayerId(),
                        offer.getPlayerName(), offer.getSellerAlias(),
                        viewerId.equals(offer.getSellerId())),
                new MarketP2POfferView.Availability(offer.getRemainingQuantity(), status,
                        purchasable), quoteView);
    }

    private static QuoteVersion quoteFrom(MarketP2POfferProjection offer) {
        if (offer.getQuoteId() == null || offer.getQuoteVersion() == null
                || offer.getUnitPrice() == null || offer.getPublishedAt() == null
                || offer.getValidFrom() == null || offer.getQuoteValidityStatus() == null) {
            return null;
        }
        return new QuoteVersion(offer.getQuoteId(), offer.getQuoteVersion(), offer.getPlayerId(),
                offer.getUnitPrice(), offer.getPublishedAt(), offer.getValidFrom(),
                offer.getValidUntil(), QuoteValidityStatus.valueOf(offer.getQuoteValidityStatus()),
                offer.getStrategyReference());
    }
}
