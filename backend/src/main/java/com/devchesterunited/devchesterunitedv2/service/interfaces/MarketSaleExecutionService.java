package com.devchesterunited.devchesterunitedv2.service.interfaces;

import com.devchesterunited.devchesterunitedv2.service.dto.MarketIdempotencyClaim;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketSaleRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketSaleResult;

/** Núcleo transaccional de una venta ya reclamada. */
public interface MarketSaleExecutionService {
    MarketSaleResult ejecutar(MarketSaleRequest request, MarketIdempotencyClaim claim);
}
