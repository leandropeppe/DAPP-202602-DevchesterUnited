package com.devchesterunited.devchesterunitedv2.service.interfaces.market;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2POfferPageResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2POfferQuery;

/** Consulta autenticada y de solo lectura del libro de ofertas P2P. */
public interface MarketP2POfferQueryService {
    MarketP2POfferPageResult consultar(MarketP2POfferQuery query);
}
