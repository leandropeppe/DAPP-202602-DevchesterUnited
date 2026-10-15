package com.devchesterunited.devchesterunitedv2.service.interfaces;

import com.devchesterunited.devchesterunitedv2.service.dto.QuoteVersion;
import java.util.UUID;

/** Valida la identidad exacta y la vigencia de una cotización antes de operar. */
public interface MarketQuoteValidationService {
    QuoteVersion validar(UUID playerId, UUID quoteId, long version);
}
