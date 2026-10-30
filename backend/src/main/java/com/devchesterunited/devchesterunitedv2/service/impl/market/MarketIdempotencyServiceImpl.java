package com.devchesterunited.devchesterunitedv2.service.impl.market;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.SolicitudIdempotenciaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.SolicitudIdempotenciaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.market.*;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketIdempotencyExecutionPort;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketIdempotencyRejectionFinalizationPort;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketIdempotencyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** Persistencia y transiciones locales de idempotencia coordinadas por lock transaccional. */
@Service
public class MarketIdempotencyServiceImpl implements MarketIdempotencyService,
        MarketIdempotencyRejectionFinalizationPort, MarketIdempotencyExecutionPort {
    private final SolicitudIdempotenciaMercadoRepository solicitudes;
    private final Clock clock;
    private final MarketOperationExecutionLock executionLock;
    private final MarketIdempotencyClaimService claimService;

    @Autowired
    public MarketIdempotencyServiceImpl(SolicitudIdempotenciaMercadoRepository solicitudes,
            Clock clock, MarketOperationExecutionLock executionLock,
            MarketIdempotencyClaimService claimService) {
        this.solicitudes = solicitudes;
        this.clock = clock;
        this.executionLock = executionLock;
        this.claimService = claimService;
    }

    public MarketIdempotencyServiceImpl(SolicitudIdempotenciaMercadoRepository solicitudes,
            Clock clock, Duration processingTimeout, MarketOperationExecutionLock executionLock) {
        this(solicitudes, clock, executionLock,
                new MarketIdempotencyClaimService(solicitudes, clock,
                        processingTimeout, executionLock));
    }

    @Override
    @Transactional
    public MarketIdempotencyResult reclamar(MarketIdempotencyRequest request) {
        return claimService.reclamarConPropiedad(request).resultado();
    }

    @Override
    @Transactional
    public MarketIdempotencyClaim reclamarConPropiedad(MarketIdempotencyRequest request) {
        return claimService.reclamarConPropiedad(request);
    }

    @Override
    @Transactional
    public MarketIdempotencyClaim reclamarConPropiedad(MarketP2PIdempotencyRequest request) {
        return claimService.reclamarConPropiedad(request);
    }

    @Override
    @Transactional
    public MarketIdempotencyClaim reclamarConPropiedad(MarketP2PCancelCommand request) {
        return claimService.reclamarConPropiedad(request);
    }

    @Override
    @Transactional
    public MarketIdempotencyResult confirmar(UUID solicitudId, UUID tokenEjecucion,
            UUID referenciaResultado) {
        SolicitudIdempotenciaMercadoEntity entity = findWithoutLock(solicitudId);
        if (!executionLock.tryAcquire(entity.getUsuarioId(), entity.getClaveIdempotencia())) {
            return toResult(entity);
        }
        entity = findForTransition(solicitudId);
        if (isFinal(entity)) {
            return toResult(entity);
        }
        try {
            entity.confirmar(tokenEjecucion, referenciaResultado, clock.instant());
        } catch (IllegalStateException exception) {
            throw staleRequest();
        }
        return toResult(entity);
    }

    @Override
    @Transactional
    public MarketIdempotencyResult rechazar(UUID solicitudId, UUID tokenEjecucion) {
        SolicitudIdempotenciaMercadoEntity entity = findWithoutLock(solicitudId);
        if (!executionLock.tryAcquire(entity.getUsuarioId(), entity.getClaveIdempotencia())) {
            return toResult(entity);
        }
        entity = findForTransition(solicitudId);
        if (isFinal(entity)) {
            return toResult(entity);
        }
        try {
            entity.rechazar(tokenEjecucion, clock.instant());
        } catch (IllegalStateException exception) {
            throw staleRequest();
        }
        return toResult(entity);
    }

    /** Finaliza sin abrir otra transacción y permite distinguir un no-op de una transición. */
    @Override
    @Transactional
    public MarketRejectionFinalizationResult rechazarParaFinalizacion(
            UUID solicitudId, UUID tokenEjecucion) {
        SolicitudIdempotenciaMercadoEntity entity = findWithoutLock(solicitudId);
        if (!executionLock.tryAcquire(entity.getUsuarioId(), entity.getClaveIdempotencia())) {
            return new MarketRejectionFinalizationResult(toResult(entity), false);
        }
        entity = findForTransition(solicitudId);
        if (!SolicitudIdempotenciaMercadoEntity.STATUS_PROCESSING.equals(entity.getStatus())
                || tokenEjecucion == null
                || !tokenEjecucion.equals(entity.getTokenEjecucion())) {
            return new MarketRejectionFinalizationResult(toResult(entity), false);
        }
        entity.rechazar(tokenEjecucion, clock.instant());
        return new MarketRejectionFinalizationResult(toResult(entity), true);
    }

    /** Reacquire el advisory lock y valida el token mientras el núcleo sigue abierto. */
    @Override
    @Transactional
    public void asegurarPropiedadDeEjecucion(UUID solicitudId, UUID tokenEjecucion) {
        SolicitudIdempotenciaMercadoEntity entity = findWithoutLock(solicitudId);
        if (!executionLock.tryAcquire(entity.getUsuarioId(), entity.getClaveIdempotencia())) {
            throw staleRequest();
        }
        entity = findForTransition(solicitudId);
        if (!SolicitudIdempotenciaMercadoEntity.STATUS_PROCESSING.equals(entity.getStatus())
                || tokenEjecucion == null
                || !tokenEjecucion.equals(entity.getTokenEjecucion())) {
            throw staleRequest();
        }
    }

    @Override
    @Transactional
    public MarketIdempotencyResult marcarIncierta(UUID solicitudId, UUID tokenEjecucion) {
        SolicitudIdempotenciaMercadoEntity entity = findWithoutLock(solicitudId);
        if (!executionLock.tryAcquire(entity.getUsuarioId(), entity.getClaveIdempotencia())) {
            return toResult(entity);
        }
        entity = findForTransition(solicitudId);
        if (isFinal(entity)) {
            return toResult(entity);
        }
        try {
            entity.marcarIncierta(tokenEjecucion, clock.instant());
        } catch (IllegalStateException exception) {
            throw staleRequest();
        }
        return toResult(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MarketIdempotencyResult> recuperar(UUID usuarioId, String claveIdempotencia) {
        if (usuarioId == null || claveIdempotencia == null || claveIdempotencia.isBlank()) {
            throw new TokenMarketException(TokenMarketException.Reason.INVALID_INPUT,
                    "La identidad y la clave de idempotencia son obligatorias.");
        }
        return solicitudes.findByUsuarioIdAndClaveIdempotencia(usuarioId, claveIdempotencia.trim())
                .map(this::toResult);
    }

    private SolicitudIdempotenciaMercadoEntity findForTransition(UUID solicitudId) {
        if (solicitudId == null) {
            throw staleRequest();
        }
        return solicitudes.findByIdForUpdate(solicitudId).orElseThrow(this::staleRequest);
    }

    private SolicitudIdempotenciaMercadoEntity findWithoutLock(UUID solicitudId) {
        if (solicitudId == null) {
            throw staleRequest();
        }
        return solicitudes.findById(solicitudId).orElseThrow(this::staleRequest);
    }

    private boolean isFinal(SolicitudIdempotenciaMercadoEntity entity) {
        return !SolicitudIdempotenciaMercadoEntity.STATUS_PROCESSING.equals(entity.getStatus());
    }

    private TokenMarketException staleRequest() {
        return new TokenMarketException(TokenMarketException.Reason.STALE_REQUEST,
                "La solicitud ya no tiene un token de ejecucion vigente.");
    }

    private MarketIdempotencyResult toResult(SolicitudIdempotenciaMercadoEntity entity) {
        return new MarketIdempotencyResult(entity.getId(), entity.getUsuarioId(),
                entity.getClaveIdempotencia(), entity.getTipoOperacion(), entity.getHashParametros(),
                MarketIdempotencyStatus.valueOf(entity.getStatus()), entity.getTokenEjecucion(),
                entity.getReferenciaResultado(), entity.getActualizadoEn());
    }
}
