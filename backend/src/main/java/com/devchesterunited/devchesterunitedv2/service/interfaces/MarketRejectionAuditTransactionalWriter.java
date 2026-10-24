package com.devchesterunited.devchesterunitedv2.service.interfaces;

import com.devchesterunited.devchesterunitedv2.service.dto.MarketRejectedAttempt;

/** Vía interna que exige participar de la transacción ya abierta por la finalización. */
public interface MarketRejectionAuditTransactionalWriter {
    void registrarEnTransaccionActual(MarketRejectedAttempt attempt);
}
