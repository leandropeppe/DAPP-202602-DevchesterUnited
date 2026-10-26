package com.devchesterunited.devchesterunitedv2.service.interfaces.market;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPurchaseSettlement;
import java.util.UUID;

/** Atomic purchase mutations; orchestration and validation remain in T046. */
public interface MarketPurchasePersistenceService {
    UUID persistirCompra(MarketPurchaseSettlement settlement);
}
