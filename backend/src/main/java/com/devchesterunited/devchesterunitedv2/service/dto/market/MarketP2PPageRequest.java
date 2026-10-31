package com.devchesterunited.devchesterunitedv2.service.dto.market;

/** Parámetros internos de paginación P2P, sin acoplamiento al transporte HTTP. */
public record MarketP2PPageRequest(int page, int limit) {
    public MarketP2PPageRequest {
        if (page < 1 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("La paginación P2P no es válida.");
        }
    }
}
