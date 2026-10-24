package com.devchesterunited.devchesterunitedv2.service.interfaces;

import com.devchesterunited.devchesterunitedv2.service.dto.MarketRejectedAttempt;

/** Contrato interno para registrar auditoria de intentos rechazados. */
public interface MarketRejectionAuditService {
    void registrar(MarketRejectedAttempt attempt);
}
