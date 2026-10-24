package com.devchesterunited.devchesterunitedv2.service.interfaces.market;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyClaim;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPurchaseRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPurchaseResult;

/** Núcleo transaccional de una compra ya reclamada. */
public interface MarketPurchaseExecutionService {
    MarketPurchaseResult ejecutar(MarketPurchaseRequest request, MarketIdempotencyClaim claim);
}
