package com.devchesterunited.devchesterunitedv2.service.dto.market;

import java.util.UUID;

/** Comando interno para una futura cancelación P2P. */
public record MarketP2PCancelCommand(UUID usuarioId, UUID offerId,
        String claveIdempotencia) { }
