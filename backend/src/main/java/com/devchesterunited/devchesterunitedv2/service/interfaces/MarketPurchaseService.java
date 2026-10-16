package com.devchesterunited.devchesterunitedv2.service.interfaces;

import com.devchesterunited.devchesterunitedv2.service.dto.MarketPurchaseRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketPurchaseResult;

/** Application boundary for an immediate, atomic purchase against SUPERUSER. */
public interface MarketPurchaseService {
    MarketPurchaseResult comprar(MarketPurchaseRequest request);
}
