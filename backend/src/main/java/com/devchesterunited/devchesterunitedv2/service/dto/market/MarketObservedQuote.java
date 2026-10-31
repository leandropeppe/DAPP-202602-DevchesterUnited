package com.devchesterunited.devchesterunitedv2.service.dto.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Cotización observada por el cliente; nunca constituye autoridad de precio. */
public record MarketObservedQuote(UUID quoteId, Long version, BigDecimal unitPrice,
        Instant validFrom, Instant validUntil, QuoteValidityStatus status) {
    public MarketObservedQuote {
        if (quoteId == null || version == null || version <= 0 || unitPrice == null
                || unitPrice.signum() <= 0 || status == null) {
            throw new IllegalArgumentException("La cotización observada no es válida.");
        }
    }
}
