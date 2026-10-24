package com.devchesterunited.devchesterunitedv2.service.impl.market;

import com.devchesterunited.devchesterunitedv2.service.dto.market.InicializacionMercadoResultado;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketInitializationService;
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
