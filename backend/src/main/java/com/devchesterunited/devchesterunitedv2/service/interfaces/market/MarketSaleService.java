package com.devchesterunited.devchesterunitedv2.service.interfaces.market;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketSaleRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketSaleResult;

/** Application boundary for an immediate, atomic sale against SUPERUSER. */
public interface MarketSaleService {
    MarketSaleResult vender(MarketSaleRequest request);
}
