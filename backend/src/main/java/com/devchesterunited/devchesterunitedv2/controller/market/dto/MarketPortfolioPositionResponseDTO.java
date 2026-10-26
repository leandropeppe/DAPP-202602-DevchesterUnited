package com.devchesterunited.devchesterunitedv2.controller.market.dto;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPortfolioPosition;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPortfolioQuote;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketQuoteAvailability;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Vista publica de una posicion abierta del portfolio. */
public record MarketPortfolioPositionResponseDTO(
        @JsonProperty("player_name")
        String nombreJugador,
        @JsonProperty("team")
        String equipo,
        @JsonProperty("league")
        String liga,
        @JsonProperty("player_id")
        UUID jugadorId,
        @JsonProperty("quantity")
        long cantidad,
        @JsonProperty("pending_acquisition_cost")
        BigDecimal costoTotalPendiente,
        @JsonProperty("weighted_average_cost")
        BigDecimal costoPromedioPonderado,
        @JsonProperty("quote_availability")
        String disponibilidadCotizacion,
        @JsonProperty("quote_id")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        UUID cotizacionId,
        @JsonProperty("quote_version")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Long versionCotizacion,
        @JsonProperty("unit_price")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        BigDecimal precioUnitario,
        @JsonProperty("published_at")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Instant publicadoEn,
        @JsonProperty("valid_from")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Instant validoDesde,
        @JsonProperty("valid_until")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Instant validoHasta,
        @JsonProperty("current_value")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        BigDecimal valorActual,
        @JsonProperty("unrealized_pnl")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        BigDecimal pnlNoRealizado) {

    public static MarketPortfolioPositionResponseDTO fromDomain(
            MarketPortfolioPosition position) {
        Objects.requireNonNull(position, "La posicion del portfolio es obligatoria.");

        MarketPortfolioQuote quote = position.cotizacion().orElse(null);
        UUID quoteId = quote == null ? null : quote.cotizacionId();
        Long quoteVersion = quote == null ? null : quote.version();
        BigDecimal unitPrice = quote == null ? null : quote.precioUnitario();
        Instant publishedAt = quote == null ? null : quote.publicadoEn();
        Instant validFrom = quote == null ? null : quote.validoDesde();
        Instant validUntil = quote == null ? null : quote.validoHasta();
        MarketQuoteAvailability availability = position.disponibilidadCotizacion();

        BigDecimal currentValue = null;
        BigDecimal unrealizedPnl = null;
        if (availability == MarketQuoteAvailability.CURRENT) {
            currentValue = position.valorActual().orElse(null);
            unrealizedPnl = position.pnlNoRealizado().orElse(null);
        }
        if (availability == MarketQuoteAvailability.ABSENT) {
            quoteId = null;
            quoteVersion = null;
            unitPrice = null;
            publishedAt = null;
            validFrom = null;
            validUntil = null;
        }

        return new MarketPortfolioPositionResponseDTO(
                position.nombreJugador(),
                position.equipo(),
                position.liga(),
                position.jugadorId(),
                position.cantidad(),
                position.costoTotalPendiente(),
                position.costoPromedioPonderado(),
                availability.name(),
                quoteId,
                quoteVersion,
                unitPrice,
                publishedAt,
                validFrom,
                validUntil,
                currentValue,
                unrealizedPnl);
    }
}
