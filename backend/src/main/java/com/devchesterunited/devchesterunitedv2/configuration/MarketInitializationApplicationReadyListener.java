package com.devchesterunited.devchesterunitedv2.configuration;

import com.devchesterunited.devchesterunitedv2.service.interfaces.MarketInitializationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Dispara la inicialización del mercado cuando la aplicación ya está disponible. */
@Component
@ConditionalOnProperty(prefix = "token-market.initialization", name = "auto-start",
        havingValue = "true", matchIfMissing = true)
public class MarketInitializationApplicationReadyListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(
            MarketInitializationApplicationReadyListener.class);

    private final MarketInitializationService inicializacion;

    public MarketInitializationApplicationReadyListener(
            MarketInitializationService inicializacion) {
        this.inicializacion = inicializacion;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void inicializarCuandoLaAplicacionEstaLista(ApplicationReadyEvent evento) {
        try {
            inicializacion.initialize();
        } catch (RuntimeException excepcion) {
            // El coordinador ya deja FAILED de forma durable. El resto de la
            // aplicación, incluida autenticación, puede continuar disponible.
            LOGGER.error("La inicialización del mercado falló durante el arranque; "
                    + "el mercado permanecerá no operativo hasta un reintento", excepcion);
        }
    }
}
