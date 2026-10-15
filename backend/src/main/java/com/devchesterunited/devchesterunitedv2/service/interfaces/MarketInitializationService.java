package com.devchesterunited.devchesterunitedv2.service.interfaces;

import com.devchesterunited.devchesterunitedv2.service.dto.InicializacionMercadoResultado;

/** Caso de uso interno para inicializar o reintentar la disponibilidad del mercado. */
public interface MarketInitializationService {
    InicializacionMercadoResultado initialize();
}
