package com.devchesterunited.devchesterunitedv2.service.dto.market;

/** Operaciones idempotentes reservadas para el mercado secundario P2P. */
public enum MarketP2POperation {
    OFFER("P2P_OFFER"),
    PURCHASE("P2P_PURCHASE"),
    CANCEL("P2P_CANCEL");

    private final String persistedValue;

    MarketP2POperation(String persistedValue) {
        this.persistedValue = persistedValue;
    }

    public String persistedValue() {
        return persistedValue;
    }
}
