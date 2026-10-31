package com.devchesterunited.devchesterunitedv2.exception;

import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteVersion;

/** Error operacional tipado del mercado sin introducir una jerarquía extensa de excepciones. */
public class TokenMarketException extends RuntimeException {

    public enum Reason {
        INACTIVE_PLAYER,
        QUOTE_UNAVAILABLE,
        INSUFFICIENT_BALANCE,
        INSUFFICIENT_INVENTORY,
        MARKET_NOT_READY,
        IDEMPOTENCY_CONFLICT,
        STALE_REQUEST,
        INVALID_INPUT,
        ACCOUNT_NOT_FOUND,
        OPERATION_NOT_FOUND,
        OFFER_NOT_FOUND,
        MANUAL_PRICE_NOT_ALLOWED,
        QUOTE_CHANGED,
        OFFER_ALREADY_CANCELLED,
        OFFER_NOT_PURCHASABLE,
        INSUFFICIENT_AVAILABLE_QUANTITY,
        INSUFFICIENT_CREDITS,
        HISTORICAL_RESULT_UNAVAILABLE
    }

    private final Reason reason;
    private final QuoteVersion currentQuote;

    public TokenMarketException(Reason reason, String message) {
        this(reason, message, null);
    }

    public TokenMarketException(Reason reason, String message, QuoteVersion currentQuote) {
        super(message);
        this.reason = java.util.Objects.requireNonNull(reason, "La causa del mercado es obligatoria.");
        this.currentQuote = currentQuote;
    }

    public Reason reason() {
        return reason;
    }

    public QuoteVersion currentQuote() {
        return currentQuote;
    }
}
