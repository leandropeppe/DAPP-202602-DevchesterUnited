package com.devchesterunited.devchesterunitedv2.service.exception;

/** Error operacional tipado del mercado sin introducir una jerarquía extensa de excepciones. */
public class TokenMarketException extends RuntimeException {

    public enum Reason {
        INACTIVE_PLAYER,
        QUOTE_UNAVAILABLE,
        INSUFFICIENT_BALANCE,
        MARKET_NOT_READY,
        IDEMPOTENCY_CONFLICT,
        STALE_REQUEST,
        INVALID_INPUT,
        ACCOUNT_NOT_FOUND
    }

    private final Reason reason;

    public TokenMarketException(Reason reason, String message) {
        super(message);
        this.reason = java.util.Objects.requireNonNull(reason, "La causa del mercado es obligatoria.");
    }

    public Reason reason() {
        return reason;
    }
}
