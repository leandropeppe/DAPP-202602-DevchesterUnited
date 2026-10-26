package com.devchesterunited.devchesterunitedv2.service.dto.market;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Resultado interno de la consulta de portfolio. */
public record MarketPortfolioResult(
        List<MarketPortfolioPosition> posicionesAbiertas,
        BigDecimal pnlRealizado,
        Optional<BigDecimal> pnlNoRealizado,
        Optional<BigDecimal> resultadoCombinado,
        boolean resultadoCombinadoCompleto,
        long jugadoresAbiertos,
        long tokensTotales,
        BigDecimal costoPendienteTotal,
        Optional<BigDecimal> valorActualTotal) {

    public MarketPortfolioResult(List<MarketPortfolioPosition> posicionesAbiertas,
            BigDecimal pnlRealizado, Optional<BigDecimal> pnlNoRealizado,
            Optional<BigDecimal> resultadoCombinado, boolean resultadoCombinadoCompleto) {
        this(posicionesAbiertas, pnlRealizado, pnlNoRealizado, resultadoCombinado,
                resultadoCombinadoCompleto,
                posicionesAbiertas.size(),
                posicionesAbiertas.stream().mapToLong(MarketPortfolioPosition::cantidad).sum(),
                posicionesAbiertas.stream()
                        .map(MarketPortfolioPosition::costoTotalPendiente)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                posicionesAbiertas.stream().allMatch(p -> p.disponibilidadCotizacion()
                        == MarketQuoteAvailability.CURRENT && p.valorActual().isPresent())
                        ? Optional.of(posicionesAbiertas.stream()
                                .flatMap(p -> p.valorActual().stream())
                                .reduce(BigDecimal.ZERO, BigDecimal::add))
                        : Optional.empty());
    }

    public MarketPortfolioResult {
        posicionesAbiertas = List.copyOf(Objects.requireNonNull(posicionesAbiertas));
        Objects.requireNonNull(pnlRealizado);
        Objects.requireNonNull(pnlNoRealizado);
        Objects.requireNonNull(resultadoCombinado);
        if (jugadoresAbiertos < 0 || tokensTotales < 0) {
            throw new IllegalArgumentException("Los totales del portfolio no pueden ser negativos");
        }
        Objects.requireNonNull(costoPendienteTotal);
        Objects.requireNonNull(valorActualTotal);
    }
}
