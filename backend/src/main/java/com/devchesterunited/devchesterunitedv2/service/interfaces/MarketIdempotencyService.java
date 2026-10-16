package com.devchesterunited.devchesterunitedv2.service.interfaces;

import com.devchesterunited.devchesterunitedv2.service.dto.MarketIdempotencyRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketIdempotencyClaim;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketIdempotencyResult;
import java.util.Optional;
import java.util.UUID;

/** Contrato interno para reclamar y recuperar solicitudes idempotentes. */
public interface MarketIdempotencyService {
    MarketIdempotencyResult reclamar(MarketIdempotencyRequest solicitud);

    /** Reclama la solicitud e informa si esta llamada posee el derecho a ejecutarla. */
    MarketIdempotencyClaim reclamarConPropiedad(MarketIdempotencyRequest solicitud);

    MarketIdempotencyResult confirmar(UUID solicitudId, UUID tokenEjecucion,
            UUID referenciaResultado);

    MarketIdempotencyResult rechazar(UUID solicitudId, UUID tokenEjecucion);

    MarketIdempotencyResult marcarIncierta(UUID solicitudId, UUID tokenEjecucion);

    Optional<MarketIdempotencyResult> recuperar(UUID usuarioId, String claveIdempotencia);
}
