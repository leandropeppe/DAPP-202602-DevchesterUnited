package com.devchesterunited.devchesterunitedv2.service.interfaces.market;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyClaim;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketSaleRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketSaleResult;

/** Núcleo transaccional de una venta ya reclamada. */
public interface MarketSaleExecutionService {
    MarketSaleResult ejecutar(MarketSaleRequest request, MarketIdempotencyClaim claim);
}
