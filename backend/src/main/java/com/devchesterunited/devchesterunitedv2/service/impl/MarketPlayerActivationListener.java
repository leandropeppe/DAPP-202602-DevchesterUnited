package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.persistence.ContraparteMercadoRepository;
import com.devchesterunited.devchesterunitedv2.service.event.JugadorActivadoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Emite el suministro inicial sólo después de confirmar el alta del catálogo. */
@Component
public class MarketPlayerActivationListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarketPlayerActivationListener.class);
    private static final String TIPO_SUPERUSER = "SUPERUSER";

    private final MarketTokenIssuanceService emisiones;
    private final ContraparteMercadoRepository contrapartes;

    public MarketPlayerActivationListener(MarketTokenIssuanceService emisiones,
            ContraparteMercadoRepository contrapartes) {
        this.emisiones = emisiones;
        this.contrapartes = contrapartes;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void emitirTrasConfirmacion(JugadorActivadoEvent evento) {
        try {
            contrapartes.findByTipoContraparte(TIPO_SUPERUSER)
                    .ifPresentOrElse(
                            contraparte -> emisiones.emitirEnPrimeraActivacion(
                                    evento.jugadorId(), contraparte.getId()),
                            () -> LOGGER.info(
                                    "Se difiere la emisión del jugador {} porque aún no existe SUPERUSER",
                                    evento.jugadorId()));
        } catch (RuntimeException excepcion) {
            // La sincronización del catálogo ya fue confirmada. La recuperación
            // queda a cargo del escaneo idempotente del próximo bootstrap.
            LOGGER.warn("No se pudo emitir tokens para el jugador {} después del commit",
                    evento.jugadorId(), excepcion);
        }
    }
}
