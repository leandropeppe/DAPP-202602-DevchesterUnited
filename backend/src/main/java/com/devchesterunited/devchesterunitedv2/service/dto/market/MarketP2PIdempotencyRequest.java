package com.devchesterunited.devchesterunitedv2.service.dto.market;

import java.util.UUID;

/** Solicitud de claim P2P que conserva la identidad de la oferta. */
public record MarketP2PIdempotencyRequest(UUID usuarioId, String claveIdempotencia,
        MarketP2PPurchaseIdentity identidad) {
    public void validar() {
        if (usuarioId == null || claveIdempotencia == null || claveIdempotencia.isBlank()
                || claveIdempotencia.trim().length() > 200 || identidad == null) {
            throw new IllegalArgumentException("La solicitud de idempotencia P2P no es valida.");
        }
        identidad.validar();
    }
}
