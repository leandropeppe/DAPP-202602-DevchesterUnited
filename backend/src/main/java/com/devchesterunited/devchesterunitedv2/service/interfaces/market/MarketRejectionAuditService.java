package com.devchesterunited.devchesterunitedv2.service.interfaces.market;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketRejectedAttempt;

/** Contrato interno para registrar auditoria de intentos rechazados. */
public interface MarketRejectionAuditService {
    void registrar(MarketRejectedAttempt attempt);
}
