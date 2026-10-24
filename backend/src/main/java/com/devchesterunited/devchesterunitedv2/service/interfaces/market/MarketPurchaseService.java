package com.devchesterunited.devchesterunitedv2.service.interfaces.market;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPurchaseRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPurchaseResult;

/** Application boundary for an immediate, atomic purchase against SUPERUSER. */
public interface MarketPurchaseService {
    MarketPurchaseResult comprar(MarketPurchaseRequest request);
}
