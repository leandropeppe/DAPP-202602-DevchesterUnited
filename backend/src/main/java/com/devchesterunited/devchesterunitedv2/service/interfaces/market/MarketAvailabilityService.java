package com.devchesterunited.devchesterunitedv2.service.interfaces.market;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketAvailabilityResult;

/** Consulta de solo lectura del estado y disponibilidad operativa del mercado. */
public interface MarketAvailabilityService {
    MarketAvailabilityResult consultarJugadores(int pagina, int limite);
}
