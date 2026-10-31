package com.devchesterunited.devchesterunitedv2.service.dto.market;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Resultado interno de la consulta de portfolio. */
public record MarketPortfolioResult(List<MarketPortfolioPosition> posicionesAbiertas,
        Summary summary, Totals totals) {

    public record Summary(BigDecimal pnlRealizado, Optional<BigDecimal> pnlNoRealizado,
            Optional<BigDecimal> resultadoCombinado, boolean resultadoCombinadoCompleto) {
        public Summary {
            Objects.requireNonNull(pnlRealizado);
            Objects.requireNonNull(pnlNoRealizado);
            Objects.requireNonNull(resultadoCombinado);
        }
    }

    public record Totals(long jugadoresAbiertos, long tokensTotales,
            BigDecimal costoPendienteTotal, Optional<BigDecimal> valorActualTotal) {
        public Totals {
            if (jugadoresAbiertos < 0 || tokensTotales < 0) {
                throw new IllegalArgumentException("Los totales del portfolio no pueden ser negativos");
            }
            Objects.requireNonNull(costoPendienteTotal);
            Objects.requireNonNull(valorActualTotal);
        }
    }

    public MarketPortfolioResult(List<MarketPortfolioPosition> posicionesAbiertas,
            BigDecimal pnlRealizado, Optional<BigDecimal> pnlNoRealizado,
            Optional<BigDecimal> resultadoCombinado, boolean resultadoCombinadoCompleto) {
        this(posicionesAbiertas,
                new Summary(pnlRealizado, pnlNoRealizado, resultadoCombinado,
                        resultadoCombinadoCompleto),
                new Totals(posicionesAbiertas.size(),
                        posicionesAbiertas.stream().mapToLong(MarketPortfolioPosition::cantidad).sum(),
                        posicionesAbiertas.stream()
                                .map(MarketPortfolioPosition::costoTotalPendiente)
                                .reduce(BigDecimal.ZERO, BigDecimal::add),
                        posicionesAbiertas.stream().allMatch(p ->
                                p.disponibilidadCotizacion() == MarketQuoteAvailability.CURRENT
                                        && p.valorActual().isPresent())
                                ? Optional.of(posicionesAbiertas.stream()
                                        .flatMap(p -> p.valorActual().stream())
                                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                                : Optional.empty()));
    }

    public MarketPortfolioResult {
        posicionesAbiertas = List.copyOf(Objects.requireNonNull(posicionesAbiertas));
        Objects.requireNonNull(summary);
        Objects.requireNonNull(totals);
    }

    public BigDecimal pnlRealizado() { return summary.pnlRealizado(); }
    public Optional<BigDecimal> pnlNoRealizado() { return summary.pnlNoRealizado(); }
    public Optional<BigDecimal> resultadoCombinado() { return summary.resultadoCombinado(); }
    public boolean resultadoCombinadoCompleto() { return summary.resultadoCombinadoCompleto(); }
    public long jugadoresAbiertos() { return totals.jugadoresAbiertos(); }
    public long tokensTotales() { return totals.tokensTotales(); }
    public BigDecimal costoPendienteTotal() { return totals.costoPendienteTotal(); }
    public Optional<BigDecimal> valorActualTotal() { return totals.valorActualTotal(); }
}
