package com.devchesterunited.devchesterunitedv2.service.interfaces.market;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketSaleSettlement;
import java.util.UUID;

/**
 * Atomic sale mutations owned by T053. T052 owns the earlier orchestration
 * locks and invokes this boundary within the caller transaction.
 */
public interface MarketSalePersistenceService {
    UUID persistirVenta(MarketSaleSettlement settlement);
}
