package com.devchesterunited.devchesterunitedv2.service.interfaces.market;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseCommand;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PQuoteIdentity;

import java.util.UUID;

/** Contrato interno para comprar ofertas P2P; el settlement se implementa despues. */
public interface MarketP2PPurchaseService {
    MarketP2PPurchaseResult comprar(UUID usuarioId, String claveIdempotencia, UUID offerId,
            Long quantity, MarketP2PQuoteIdentity quote);

    MarketP2PPurchaseResult comprar(MarketP2PPurchaseCommand command);
}
