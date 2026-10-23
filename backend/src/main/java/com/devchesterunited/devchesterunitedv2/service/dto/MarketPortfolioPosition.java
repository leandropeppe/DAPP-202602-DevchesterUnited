package com.devchesterunited.devchesterunitedv2.service.dto;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Posición abierta de un jugador dentro del portfolio. */
public record MarketPortfolioPosition(
        UUID jugadorId,
        long cantidad,
        BigDecimal costoTotalPendiente,
        BigDecimal costoPromedioPonderado,
        MarketQuoteAvailability disponibilidadCotizacion,
        Optional<MarketPortfolioQuote> cotizacion,
        Optional<BigDecimal> valorActual,
        Optional<BigDecimal> pnlNoRealizado,
        String nombreJugador,
        String equipo,
        String liga) {

    public MarketPortfolioPosition(UUID jugadorId, long cantidad, BigDecimal costoTotalPendiente,
            BigDecimal costoPromedioPonderado, MarketQuoteAvailability disponibilidadCotizacion,
            Optional<MarketPortfolioQuote> cotizacion, Optional<BigDecimal> valorActual,
            Optional<BigDecimal> pnlNoRealizado) {
        this(jugadorId, cantidad, costoTotalPendiente, costoPromedioPonderado,
                disponibilidadCotizacion, cotizacion, valorActual, pnlNoRealizado,
                null, null, null);
    }

    public MarketPortfolioPosition {
        Objects.requireNonNull(jugadorId);
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad de una posición abierta debe ser positiva");
        }
        Objects.requireNonNull(costoTotalPendiente);
        Objects.requireNonNull(costoPromedioPonderado);
        Objects.requireNonNull(disponibilidadCotizacion);
        Objects.requireNonNull(cotizacion);
        Objects.requireNonNull(valorActual);
        Objects.requireNonNull(pnlNoRealizado);
    }

    public MarketPortfolioPosition conDescripcion(String nombreJugador, String equipo,
            String liga) {
        return new MarketPortfolioPosition(jugadorId, cantidad, costoTotalPendiente,
                costoPromedioPonderado, disponibilidadCotizacion, cotizacion, valorActual,
                pnlNoRealizado, nombreJugador, equipo, liga);
    }
}
