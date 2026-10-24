package com.devchesterunited.devchesterunitedv2.service.interfaces.market;

import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteVersion;
import java.util.UUID;

/** Valida la identidad exacta y la vigencia de una cotización antes de operar. */
public interface MarketQuoteValidationService {
    QuoteVersion validar(UUID playerId, UUID quoteId, long version);
}
