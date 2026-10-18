package com.devchesterunited.devchesterunitedv2.service.interfaces;

import com.devchesterunited.devchesterunitedv2.service.dto.MarketSaleSettlement;
import java.util.UUID;

/**
 * Atomic sale mutations owned by T053. T052 owns the earlier orchestration
 * locks and invokes this boundary within the caller transaction.
 */
public interface MarketSalePersistenceService {
    UUID persistirVenta(MarketSaleSettlement settlement);
}
