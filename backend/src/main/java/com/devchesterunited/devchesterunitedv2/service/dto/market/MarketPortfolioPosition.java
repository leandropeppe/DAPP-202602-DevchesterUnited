package com.devchesterunited.devchesterunitedv2.service.dto.market;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Posicion abierta de un jugador dentro del portfolio. */
public record MarketPortfolioPosition(Identity identity, Quantities quantities,
        Costs costs, Valuation valuation) {

    public record Identity(UUID jugadorId, String nombreJugador, String equipo, String liga) { }

    public record Quantities(long cantidad, long cantidadReservada) {
        public Quantities {
            if (cantidad <= 0) {
                throw new IllegalArgumentException("La cantidad de una posición abierta debe ser positiva");
            }
            if (cantidadReservada < 0 || cantidadReservada > cantidad) {
                throw new IllegalArgumentException("La reserva de la posición no es válida");
            }
        }
    }

    public record Costs(BigDecimal costoTotalPendiente, BigDecimal costoPromedioPonderado) {
        public Costs {
            Objects.requireNonNull(costoTotalPendiente);
            Objects.requireNonNull(costoPromedioPonderado);
        }
    }

    public record Valuation(MarketQuoteAvailability disponibilidadCotizacion,
            Optional<MarketPortfolioQuote> cotizacion, Optional<BigDecimal> valorActual,
            Optional<BigDecimal> pnlNoRealizado) {
        public Valuation {
            Objects.requireNonNull(disponibilidadCotizacion);
            Objects.requireNonNull(cotizacion);
            Objects.requireNonNull(valorActual);
            Objects.requireNonNull(pnlNoRealizado);
        }
    }

    public MarketPortfolioPosition {
        Objects.requireNonNull(identity);
        Objects.requireNonNull(identity.jugadorId());
        Objects.requireNonNull(quantities);
        Objects.requireNonNull(costs);
        Objects.requireNonNull(valuation);
    }

    public MarketPortfolioPosition(UUID jugadorId, long cantidad, long cantidadReservada,
            Costs costs, Valuation valuation) {
        this(new Identity(jugadorId, null, null, null),
                new Quantities(cantidad, cantidadReservada), costs, valuation);
    }

    public MarketPortfolioPosition(UUID jugadorId, long cantidad, Costs costs,
            Valuation valuation) {
        this(jugadorId, cantidad, 0L, costs, valuation);
    }

    public UUID jugadorId() { return identity.jugadorId(); }
    public long cantidad() { return quantities.cantidad(); }
    public long cantidadReservada() { return quantities.cantidadReservada(); }
    public BigDecimal costoTotalPendiente() { return costs.costoTotalPendiente(); }
    public BigDecimal costoPromedioPonderado() { return costs.costoPromedioPonderado(); }
    public MarketQuoteAvailability disponibilidadCotizacion() {
        return valuation.disponibilidadCotizacion();
    }
    public Optional<MarketPortfolioQuote> cotizacion() { return valuation.cotizacion(); }
    public Optional<BigDecimal> valorActual() { return valuation.valorActual(); }
    public Optional<BigDecimal> pnlNoRealizado() { return valuation.pnlNoRealizado(); }
    public String nombreJugador() { return identity.nombreJugador(); }
    public String equipo() { return identity.equipo(); }
    public String liga() { return identity.liga(); }

    public MarketPortfolioPosition conDescripcion(String nombreJugador, String equipo,
            String liga) {
        return new MarketPortfolioPosition(
                new Identity(jugadorId(), nombreJugador, equipo, liga), quantities, costs,
                valuation);
    }
}
