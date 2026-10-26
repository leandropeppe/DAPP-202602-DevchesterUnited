package com.devchesterunited.devchesterunitedv2.service.impl.market;

import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.EmisionTokensMercadoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Emite el suministro inicial de un jugador sin volver a emitirlo al reactivarlo. */
@Service
public class MarketTokenIssuanceService {
    static final long INITIAL_ISSUANCE = MarketTokenIssuanceTransactionalService.INITIAL_ISSUANCE;

    private final MarketTokenIssuanceTransactionalService transactionalService;

    @Autowired
    public MarketTokenIssuanceService(
            MarketTokenIssuanceTransactionalService transactionalService) {
        this.transactionalService = transactionalService;
    }

    public MarketTokenIssuanceService(JugadorRepository jugadores,
            EmisionTokensMercadoRepository emisiones, Clock clock) {
        this(new MarketTokenIssuanceTransactionalService(jugadores, emisiones, clock));
    }

    /** Emite todos los jugadores activos en un orden estable durante el bootstrap. */
    @Transactional
    public void emitirJugadoresActivos(UUID contraparteId) {
        transactionalService.emitirJugadoresActivosYObtener(contraparteId);
    }

    /**
     * Emite, valida y devuelve los jugadores que quedaron elegibles para la
     * cotizaciÃ³n inicial dentro de la misma transacciÃ³n del bootstrap.
     */
    @Transactional
    public List<UUID> emitirJugadoresActivosYObtener(UUID contraparteId) {
        return transactionalService.emitirJugadoresActivosYObtener(contraparteId);
    }

    /**
     * Flujo utilizado por el primer evento de activaciÃ³n. El jugador se bloquea
     * antes de leer su estado, por lo que una desactivaciÃ³n concurrente no puede
     * producir una emisiÃ³n contra un estado que ya dejÃ³ de ser activo.
     */
    @Transactional
    public void emitirEnPrimeraActivacion(UUID jugadorId, UUID contraparteId) {
        transactionalService.emitirEnPrimeraActivacion(jugadorId, contraparteId);
    }
}
