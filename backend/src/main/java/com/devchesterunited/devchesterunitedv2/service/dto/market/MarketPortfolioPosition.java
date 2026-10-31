package com.devchesterunited.devchesterunitedv2.service.dto.market;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Posicion abierta de un jugador dentro del portfolio. */
public record MarketPortfolioPosition(
        UUID jugadorId,
        long cantidad,
        long cantidadReservada,
        BigDecimal costoTotalPendiente,
        BigDecimal costoPromedioPonderado,
        MarketQuoteAvailability disponibilidadCotizacion,
        Optional<MarketPortfolioQuote> cotizacion,
        Optional<BigDecimal> valorActual,
        Optional<BigDecimal> pnlNoRealizado,
        String nombreJugador,
        String equipo,
        String liga) {

    /** Constructor compatible: las posiciones históricas no tenían reserva expuesta. */
    public MarketPortfolioPosition(UUID jugadorId, long cantidad,
            BigDecimal costoTotalPendiente, BigDecimal costoPromedioPonderado,
            MarketQuoteAvailability disponibilidadCotizacion,
            Optional<MarketPortfolioQuote> cotizacion, Optional<BigDecimal> valorActual,
            Optional<BigDecimal> pnlNoRealizado) {
        this(jugadorId, cantidad, 0L, costoTotalPendiente, costoPromedioPonderado,
                disponibilidadCotizacion, cotizacion, valorActual, pnlNoRealizado,
                null, null, null);
    }

    public MarketPortfolioPosition(UUID jugadorId, long cantidad, long cantidadReservada,
            BigDecimal costoTotalPendiente, BigDecimal costoPromedioPonderado,
            MarketQuoteAvailability disponibilidadCotizacion,
            Optional<MarketPortfolioQuote> cotizacion, Optional<BigDecimal> valorActual,
            Optional<BigDecimal> pnlNoRealizado) {
        this(jugadorId, cantidad, cantidadReservada, costoTotalPendiente,
                costoPromedioPonderado, disponibilidadCotizacion, cotizacion, valorActual,
                pnlNoRealizado, null, null, null);
    }

    public MarketPortfolioPosition(UUID jugadorId, long cantidad,
            BigDecimal costoTotalPendiente, BigDecimal costoPromedioPonderado,
            MarketQuoteAvailability disponibilidadCotizacion,
            Optional<MarketPortfolioQuote> cotizacion, Optional<BigDecimal> valorActual,
            Optional<BigDecimal> pnlNoRealizado, String nombreJugador, String equipo,
            String liga) {
        this(jugadorId, cantidad, 0L, costoTotalPendiente, costoPromedioPonderado,
                disponibilidadCotizacion, cotizacion, valorActual, pnlNoRealizado,
                nombreJugador, equipo, liga);
    }

    public MarketPortfolioPosition {
        Objects.requireNonNull(jugadorId);
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad de una posición abierta debe ser positiva");
        }
        if (cantidadReservada < 0 || cantidadReservada > cantidad) {
            throw new IllegalArgumentException("La reserva de la posición no es válida");
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
        return new MarketPortfolioPosition(jugadorId, cantidad, cantidadReservada,
                costoTotalPendiente, costoPromedioPonderado, disponibilidadCotizacion,
                cotizacion, valorActual, pnlNoRealizado, nombreJugador, equipo, liga);
    }
}
