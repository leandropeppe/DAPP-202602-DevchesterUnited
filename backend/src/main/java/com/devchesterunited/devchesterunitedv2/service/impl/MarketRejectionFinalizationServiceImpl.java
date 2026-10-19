package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.service.dto.MarketRejectedAttempt;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketRejectionFinalizationResult;
import com.devchesterunited.devchesterunitedv2.service.interfaces.MarketIdempotencyRejectionFinalizationPort;
import com.devchesterunited.devchesterunitedv2.service.interfaces.MarketRejectionAuditTransactionalWriter;
import com.devchesterunited.devchesterunitedv2.service.interfaces.MarketRejectionFinalizationService;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Núcleo transaccional de la finalización conjunta de un rechazo. */
@Service
public class MarketRejectionFinalizationServiceImpl
        implements MarketRejectionFinalizationService {
    private final MarketIdempotencyRejectionFinalizationPort idempotency;
    private final MarketRejectionAuditTransactionalWriter auditWriter;

    public MarketRejectionFinalizationServiceImpl(
            MarketIdempotencyRejectionFinalizationPort idempotency,
            MarketRejectionAuditTransactionalWriter auditWriter) {
        this.idempotency = Objects.requireNonNull(idempotency);
        this.auditWriter = Objects.requireNonNull(auditWriter);
    }

    @Override
    @Transactional
    public MarketRejectionFinalizationResult finalizarRechazo(
            UUID solicitudId, UUID tokenEjecucion, MarketRejectedAttempt attempt) {
        MarketRejectionFinalizationResult result =
                idempotency.rechazarParaFinalizacion(solicitudId, tokenEjecucion);
        if (result.transicionRealizada()) {
            auditWriter.registrarEnTransaccionActual(attempt);
        }
        return result;
    }
}
