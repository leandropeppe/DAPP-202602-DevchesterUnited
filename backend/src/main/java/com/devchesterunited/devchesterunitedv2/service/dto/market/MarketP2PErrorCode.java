package com.devchesterunited.devchesterunitedv2.service.dto.market;

/** Códigos P2P internos alineados al contrato, sin mensajes sensibles. */
public enum MarketP2PErrorCode {
    MANUAL_PRICE_NOT_ALLOWED,
    INVALID_QUANTITY,
    QUOTE_CHANGED,
    OFFER_ALREADY_CANCELLED,
    OFFER_NOT_PURCHASABLE,
    INSUFFICIENT_AVAILABLE_QUANTITY,
    INSUFFICIENT_CREDITS
}
