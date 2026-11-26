package com.devchesterunited.devchesterunitedv2.configuration.auditoria;

import java.util.Set;

/** Allowlist cerrada. No se permite ninguna operación implícita ni alternativa. */
public final class AuditoriaOperationCatalog {
    public static final String CATALOG_SYNC = "catalog.sync";
    public static final String STATISTICS_SYNC = "statistics.sync";
    public static final String VALUATION_MANUAL = "valuation.manual";
    public static final String VALUATION_WEEKLY = "valuation.weekly";
    public static final String MARKET_PURCHASE = "market.purchase";
    public static final String MARKET_SALE = "market.sale";
    public static final String MARKET_P2P_OFFER_PUBLISH = "market.p2p.offer.publish";
    public static final String MARKET_P2P_PURCHASE = "market.p2p.purchase";
    public static final String MARKET_P2P_OFFER_CANCEL = "market.p2p.offer.cancel";
    public static final String MARKET_INITIALIZE = "market.initialize";

    private static final Set<String> OPERATIONS = Set.of(CATALOG_SYNC, STATISTICS_SYNC, VALUATION_MANUAL,
            VALUATION_WEEKLY, MARKET_PURCHASE, MARKET_SALE, MARKET_P2P_OFFER_PUBLISH, MARKET_P2P_PURCHASE,
            MARKET_P2P_OFFER_CANCEL, MARKET_INITIALIZE);

    private AuditoriaOperationCatalog() { }
    public static Set<String> operations() { return OPERATIONS; }
    public static boolean isSupported(String operation) { return operation != null && OPERATIONS.contains(operation); }
}
