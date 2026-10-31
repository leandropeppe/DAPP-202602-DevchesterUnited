package com.devchesterunited.devchesterunitedv2.service.interfaces.market;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseCommand;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseResult;

/** Contrato interno para comprar ofertas P2P; el settlement se implementa despues. */
public interface MarketP2PPurchaseService {
    MarketP2PPurchaseResult comprar(MarketP2PPurchaseCommand command);
}
