package com.devchesterunited.devchesterunitedv2.service.dto;

/** Estado visible de la cotización sin confundir historial con precio operativo. */
public enum MarketQuoteAvailability {
    CURRENT,
    HISTORICAL,
    ABSENT
}
