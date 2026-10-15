package com.devchesterunited.devchesterunitedv2.service.interfaces;

import com.devchesterunited.devchesterunitedv2.service.dto.MarketAvailabilityResult;

/** Consulta de solo lectura del estado y disponibilidad operativa del mercado. */
public interface MarketAvailabilityService {
    MarketAvailabilityResult consultarJugadores(int pagina, int limite);
}
