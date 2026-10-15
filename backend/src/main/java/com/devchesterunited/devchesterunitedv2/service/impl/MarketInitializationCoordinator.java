package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.modelo.EstadoMercado;
import com.devchesterunited.devchesterunitedv2.service.dto.InicializacionMercadoResultado;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;

/**
 * Coordina el primer incremento de inicialización sin ejecutar etapas futuras
 * ni habilitar el mercado prematuramente.
 */
@Service
public class MarketInitializationCoordinator {
    private static final String MOTIVO_FALLO_BOOTSTRAP =
            "Fallo durante bootstrap de inicializacion del mercado";

    private final MarketInitializationStateService estados;
    private final MarketInitializationBootstrap bootstrap;
    private final ReentrantLock intentoEnCurso = new ReentrantLock();

    public MarketInitializationCoordinator(
            MarketInitializationStateService estados,
            MarketInitializationBootstrap bootstrap) {
        this.estados = estados;
        this.bootstrap = bootstrap;
    }

    /**
     * Inicia o reanuda el estado y ejecuta únicamente el bootstrap T019.
     * El resultado permanece INITIALIZING hasta que las etapas futuras existan.
     */
    public InicializacionMercadoResultado coordinar() {
        intentoEnCurso.lock();
        try {
            InicializacionMercadoResultado iniciado = estados.comenzarOReanudar();
            if (iniciado.estado() == EstadoMercado.READY) {
                return iniciado;
            }
            if (iniciado.estado() != EstadoMercado.INITIALIZING) {
                throw new IllegalStateException(
                        "Estado inesperado para coordinar inicializacion: "
                                + iniciado.estado());
            }

            try {
                bootstrap.bootstrap();
                return iniciado;
            } catch (RuntimeException | Error original) {
                marcarFalloSinOcultarError(iniciado.epoca(), original);
                throw original;
            }
        } finally {
            intentoEnCurso.unlock();
        }
    }

    private void marcarFalloSinOcultarError(long epoch, Throwable original) {
        try {
            estados.marcarFailed(epoch, MOTIVO_FALLO_BOOTSTRAP);
        } catch (RuntimeException | Error falloAlRegistrar) {
            original.addSuppressed(falloAlRegistrar);
        }
    }
}
