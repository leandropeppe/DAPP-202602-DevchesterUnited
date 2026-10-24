package com.devchesterunited.devchesterunitedv2.service.interfaces.market;

import com.devchesterunited.devchesterunitedv2.service.dto.market.InicializacionMercadoResultado;

/** Caso de uso interno para inicializar o reintentar la disponibilidad del mercado. */
public interface MarketInitializationService {
    InicializacionMercadoResultado initialize();
}
