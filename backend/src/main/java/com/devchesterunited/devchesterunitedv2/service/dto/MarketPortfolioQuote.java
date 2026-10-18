package com.devchesterunited.devchesterunitedv2.service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Snapshot de la cotización asociada a una posición de portfolio. */
public record MarketPortfolioQuote(
        UUID cotizacionId,
        long version,
        BigDecimal precioUnitario,
        Instant publicadoEn,
        Instant validoDesde,
        Instant validoHasta) {

    public MarketPortfolioQuote {
        Objects.requireNonNull(cotizacionId);
        if (version <= 0) {
            throw new IllegalArgumentException("La versión de cotización debe ser positiva");
        }
        Objects.requireNonNull(precioUnitario);
        Objects.requireNonNull(publicadoEn);
        Objects.requireNonNull(validoDesde);
    }
}
