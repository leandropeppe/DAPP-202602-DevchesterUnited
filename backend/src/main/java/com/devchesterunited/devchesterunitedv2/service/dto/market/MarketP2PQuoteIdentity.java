package com.devchesterunited.devchesterunitedv2.service.dto.market;

import java.util.UUID;

/** Identidad de la cotizacion observada por el comprador P2P. */
public record MarketP2PQuoteIdentity(UUID quoteId, Long version) {
    public void validar() {
        if (quoteId == null || version == null || version <= 0) {
            throw new IllegalArgumentException("La identidad de cotizacion no es valida.");
        }
    }
}
