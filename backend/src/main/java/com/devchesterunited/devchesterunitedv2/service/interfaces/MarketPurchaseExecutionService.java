package com.devchesterunited.devchesterunitedv2.service.interfaces;

import com.devchesterunited.devchesterunitedv2.service.dto.MarketIdempotencyClaim;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketPurchaseRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketPurchaseResult;

/** Núcleo transaccional de una compra ya reclamada. */
public interface MarketPurchaseExecutionService {
    MarketPurchaseResult ejecutar(MarketPurchaseRequest request, MarketIdempotencyClaim claim);
}
