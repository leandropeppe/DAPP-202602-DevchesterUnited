package com.devchesterunited.devchesterunitedv2.service.impl.market;

import java.util.UUID;

/** Orden canónico compartido por las futuras operaciones P2P. */
public final class MarketP2PLockOrder {
    public static final String CLAIM_ADVISORY = "claim/advisory";
    public static final String MARKET_STATE = "market_state";
    public static final String OFFER = "offer";
    public static final String PLAYER = "player";
    public static final String QUOTE_HEADER = "quote_header";
    public static final String ACCOUNTS_ASC = "accounts UUID ascendente";
    public static final String HOLDINGS_ASC = "holdings (user_id, player_id) ascendente";
    public static final String MOVEMENTS_AUDIT = "movimientos/auditoría";

    private MarketP2PLockOrder() { }

    /** Compara identificadores para que dos operaciones adquieran recursos en el mismo orden. */
    public static int compareIds(UUID left, UUID right) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("Los identificadores de lock son obligatorios.");
        }
        return left.compareTo(right);
    }
}
