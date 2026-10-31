package com.devchesterunited.devchesterunitedv2.service.interfaces.market;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PCancelCommand;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PCancelResult;

/** Contrato interno para cancelar ofertas P2P de forma idempotente. */
public interface MarketP2PCancellationService {
    MarketP2PCancelResult cancelar(MarketP2PCancelCommand command);
}
