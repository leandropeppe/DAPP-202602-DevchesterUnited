package com.devchesterunited.devchesterunitedv2.service.dto;

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
        boolean resultadoCombinadoCompleto) {

    public MarketPortfolioResult {
        posicionesAbiertas = List.copyOf(Objects.requireNonNull(posicionesAbiertas));
        Objects.requireNonNull(pnlRealizado);
        Objects.requireNonNull(pnlNoRealizado);
        Objects.requireNonNull(resultadoCombinado);
    }
}
