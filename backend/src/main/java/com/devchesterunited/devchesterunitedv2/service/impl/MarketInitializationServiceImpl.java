package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.service.dto.InicializacionMercadoResultado;
import com.devchesterunited.devchesterunitedv2.service.interfaces.MarketInitializationService;
import org.springframework.stereotype.Service;

/** Fachada del caso de uso interno de inicialización del mercado. */
@Service
public class MarketInitializationServiceImpl implements MarketInitializationService {
    private final MarketInitializationCoordinator coordinador;

    public MarketInitializationServiceImpl(MarketInitializationCoordinator coordinador) {
        this.coordinador = coordinador;
    }

    /** Delega el único flujo que posee locks, epochs y etapas idempotentes. */
    @Override
    public InicializacionMercadoResultado initialize() {
        return coordinador.coordinar();
    }
}
