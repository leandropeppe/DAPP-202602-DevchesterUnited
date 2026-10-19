package com.devchesterunited.devchesterunitedv2.service.interfaces;

import com.devchesterunited.devchesterunitedv2.service.dto.MarketRejectedAttempt;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketRejectionFinalizationResult;
import java.util.UUID;

/** Finaliza atómicamente la solicitud idempotente y su auditoría de rechazo. */
public interface MarketRejectionFinalizationService {
    MarketRejectionFinalizationResult finalizarRechazo(
            UUID solicitudId, UUID tokenEjecucion, MarketRejectedAttempt attempt);
}
