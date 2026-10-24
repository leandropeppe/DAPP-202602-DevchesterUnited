package com.devchesterunited.devchesterunitedv2.service.interfaces;

import com.devchesterunited.devchesterunitedv2.service.dto.MarketRejectionFinalizationResult;
import java.util.UUID;

/** Puerto interno para finalizar un rechazo dentro de la transacción del coordinador. */
public interface MarketIdempotencyRejectionFinalizationPort {
    MarketRejectionFinalizationResult rechazarParaFinalizacion(
            UUID solicitudId, UUID tokenEjecucion);
}
