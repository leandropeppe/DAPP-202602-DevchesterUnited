package com.devchesterunited.devchesterunitedv2.service.dto.market;

import java.util.UUID;

/** Comando interno para una futura publicación P2P. */
public record MarketP2POfferCommand(UUID usuarioId, UUID jugadorId, Long cantidad,
        String claveIdempotencia) { }
