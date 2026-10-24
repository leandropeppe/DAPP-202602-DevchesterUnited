package com.devchesterunited.devchesterunitedv2.service.interfaces.market;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketRejectionFinalizationResult;
import java.util.UUID;

/** Puerto interno para finalizar un rechazo dentro de la transacción del coordinador. */
public interface MarketIdempotencyRejectionFinalizationPort {
    MarketRejectionFinalizationResult rechazarParaFinalizacion(
            UUID solicitudId, UUID tokenEjecucion);
}
