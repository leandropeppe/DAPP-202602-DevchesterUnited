package com.devchesterunited.devchesterunitedv2.service.impl.market;

import com.devchesterunited.devchesterunitedv2.modelo.market.EstadoMercado;
import com.devchesterunited.devchesterunitedv2.service.dto.market.InicializacionMercadoResultado;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.locks.ReentrantLock;

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
    private final InicializacionCreditosUsuariosService creditos;
    private final MarketInitializationExecutionLock lockPersistente;
    private final ReentrantLock intentoEnCurso = new ReentrantLock();

    @Autowired
    public MarketInitializationCoordinator(
            MarketInitializationStateService estados,
            MarketInitializationBootstrap bootstrap,
            InicializacionCreditosUsuariosService creditos,
            MarketInitializationExecutionLock lockPersistente) {
        this.estados = estados;
        this.bootstrap = bootstrap;
        this.creditos = creditos;
        this.lockPersistente = lockPersistente;
    }

    /** Compatibilidad con las pruebas del coordinador fundacional de T020. */
    public MarketInitializationCoordinator(
            MarketInitializationStateService estados,
            MarketInitializationBootstrap bootstrap) {
        this(estados, bootstrap, null, null);
    }

    /**
     * Inicia o reanuda la ejecución completa de inicialización. Las etapas
     * individuales son idempotentes y pueden reanudarse después de un FAILED o
     * de una interrupción que haya dejado el estado en INITIALIZING.
     */
    public InicializacionMercadoResultado coordinar() {
        intentoEnCurso.lock();
        try {
            if (lockPersistente != null) {
                return lockPersistente.ejecutar(this::coordinarProtegido);
            }
            return coordinarProtegido();
        } finally {
            intentoEnCurso.unlock();
        }
    }

    private InicializacionMercadoResultado coordinarProtegido() {
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
                if (creditos != null) {
                    creditos.asignarAUsuariosExistentes();
                }
                bootstrap.bootstrap();
                if (creditos == null) {
                    return iniciado;
                }
                return estados.marcarReady(iniciado.epoca()).orElseThrow(() ->
                        new IllegalStateException(
                                "La ejecución de inicialización perdió la propiedad del epoch."));
            } catch (RuntimeException original) {
                marcarFalloSinOcultarError(iniciado.epoca(), original);
                throw original;
            }
    }

    private void marcarFalloSinOcultarError(long epoch, Throwable original) {
        try {
            estados.marcarFailed(epoch, MOTIVO_FALLO_BOOTSTRAP);
        } catch (RuntimeException falloAlRegistrar) {
            original.addSuppressed(falloAlRegistrar);
        }
    }
}
