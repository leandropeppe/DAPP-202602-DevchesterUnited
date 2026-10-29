package com.devchesterunited.devchesterunitedv2.service.interfaces.market;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2POfferCommand;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2POfferResult;

/** Publica ofertas P2P sin transferir tokens ni créditos. */
public interface MarketP2POfferService {
    MarketP2POfferResult publicar(MarketP2POfferCommand command);
}
