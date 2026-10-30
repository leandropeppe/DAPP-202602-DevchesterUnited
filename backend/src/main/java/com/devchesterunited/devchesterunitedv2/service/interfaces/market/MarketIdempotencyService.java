package com.devchesterunited.devchesterunitedv2.service.interfaces.market;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyClaim;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PCancelCommand;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PIdempotencyRequest;

import java.util.Optional;
import java.util.UUID;

/** Contrato interno para reclamar y recuperar solicitudes idempotentes. */
public interface MarketIdempotencyService {
    MarketIdempotencyResult reclamar(MarketIdempotencyRequest solicitud);

    /** Reclama la solicitud e informa si esta llamada posee el derecho a ejecutarla. */
    MarketIdempotencyClaim reclamarConPropiedad(MarketIdempotencyRequest solicitud);

    default MarketIdempotencyClaim reclamarConPropiedad(MarketP2PIdempotencyRequest solicitud) {
        throw new UnsupportedOperationException("La idempotencia P2P no esta implementada.");
    }

    default MarketIdempotencyClaim reclamarConPropiedad(MarketP2PCancelCommand solicitud) {
        throw new UnsupportedOperationException("La idempotencia de cancelacion P2P no esta implementada.");
    }

    MarketIdempotencyResult confirmar(UUID solicitudId, UUID tokenEjecucion,
            UUID referenciaResultado);

    MarketIdempotencyResult rechazar(UUID solicitudId, UUID tokenEjecucion);

    MarketIdempotencyResult marcarIncierta(UUID solicitudId, UUID tokenEjecucion);

    Optional<MarketIdempotencyResult> recuperar(UUID usuarioId, String claveIdempotencia);
}
