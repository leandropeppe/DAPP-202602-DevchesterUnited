package com.devchesterunited.devchesterunitedv2.service.interfaces;

import com.devchesterunited.devchesterunitedv2.service.dto.MarketSaleRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketSaleResult;

/** Application boundary for an immediate, atomic sale against SUPERUSER. */
public interface MarketSaleService {
    MarketSaleResult vender(MarketSaleRequest request);
}
