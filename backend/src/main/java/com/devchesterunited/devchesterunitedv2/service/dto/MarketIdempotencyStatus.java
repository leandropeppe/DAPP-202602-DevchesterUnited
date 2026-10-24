package com.devchesterunited.devchesterunitedv2.service.dto;

/** Estados durables de una solicitud de operacion del mercado. */
public enum MarketIdempotencyStatus {
    PROCESSING,
    CONFIRMED,
    REJECTED,
    UNCERTAIN
}
