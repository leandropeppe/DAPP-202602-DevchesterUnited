package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.persistence.SolicitudIdempotenciaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.SolicitudIdempotenciaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketIdempotencyRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketIdempotencyResult;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketIdempotencyStatus;
import com.devchesterunited.devchesterunitedv2.service.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.interfaces.MarketIdempotencyService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persistencia y transiciones locales de idempotencia coordinadas por lock transaccional. */
@Service
public class MarketIdempotencyServiceImpl implements MarketIdempotencyService {
    private final SolicitudIdempotenciaMercadoRepository solicitudes;
    private final Clock clock;
    private final Duration processingTimeout;
    private final MarketOperationExecutionLock executionLock;

    @Autowired
    public MarketIdempotencyServiceImpl(SolicitudIdempotenciaMercadoRepository solicitudes,
            Clock clock, MarketOperationExecutionLock executionLock) {
        this(solicitudes, clock, Duration.ofMinutes(5), executionLock);
    }

    public MarketIdempotencyServiceImpl(SolicitudIdempotenciaMercadoRepository solicitudes,
            Clock clock, Duration processingTimeout, MarketOperationExecutionLock executionLock) {
        this.solicitudes = solicitudes;
        this.clock = clock;
        this.executionLock = executionLock;
        if (processingTimeout == null || processingTimeout.isNegative()
                || processingTimeout.isZero()) {
            throw new IllegalArgumentException("El timeout de PROCESSING debe ser positivo.");
        }
        this.processingTimeout = processingTimeout;
    }

    @Override
    @Transactional
    public MarketIdempotencyResult reclamar(MarketIdempotencyRequest request) {
        validarIdentidadYClave(request);
        String normalizedKey = request.claveIdempotencia().trim();
        String operation = normalizeOperation(request.tipoOperacion());
        String hash = MarketIdempotencyParameterHasher.hash(request);
        Instant now = clock.instant();

        if (!executionLock.tryAcquire(request.usuarioId(), normalizedKey)) {
            return solicitudes.findByUsuarioIdAndClaveIdempotencia(request.usuarioId(), normalizedKey)
                    .map(entity -> recoverWhileBusy(entity, operation, hash))
                    .orElseGet(() -> pendiente(request, operation, hash, now));
        }

        Optional<SolicitudIdempotenciaMercadoEntity> existing =
                solicitudes.findByUsuarioIdAndClaveForUpdate(request.usuarioId(), normalizedKey);
        if (existing.isPresent()) {
            SolicitudIdempotenciaMercadoEntity entity = existing.get();
            if (!operation.equals(entity.getTipoOperacion()) || !hash.equals(entity.getHashParametros())) {
                throw new TokenMarketException(TokenMarketException.Reason.IDEMPOTENCY_CONFLICT,
                        "La clave de idempotencia ya fue utilizada con otros parametros.");
            }
            if (SolicitudIdempotenciaMercadoEntity.STATUS_PROCESSING.equals(entity.getStatus())
                    && !now.isBefore(entity.getActualizadoEn().plus(processingTimeout))) {
                entity.reclamarNuevamente(UUID.randomUUID(), now);
            }
            return toResult(entity);
        }

        UUID executionToken = UUID.randomUUID();
        SolicitudIdempotenciaMercadoEntity entity = new SolicitudIdempotenciaMercadoEntity(
                UUID.randomUUID(), request.usuarioId(), normalizedKey, operation, hash,
                SolicitudIdempotenciaMercadoEntity.STATUS_PROCESSING, executionToken, null, now, now);
        try {
            return toResult(solicitudes.saveAndFlush(entity));
        } catch (DataIntegrityViolationException exception) {
            throw new TokenMarketException(TokenMarketException.Reason.IDEMPOTENCY_CONFLICT,
                    "La solicitud de idempotencia ya existe.");
        }
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

    private MarketIdempotencyResult pendiente(MarketIdempotencyRequest request, String operation,
            String hash, Instant now) {
        return new MarketIdempotencyResult(null, request.usuarioId(),
                request.claveIdempotencia().trim(), operation, hash,
                MarketIdempotencyStatus.PROCESSING, null, null, now);
    }

    private MarketIdempotencyResult recoverWhileBusy(
            SolicitudIdempotenciaMercadoEntity entity, String operation, String hash) {
        if (!operation.equals(entity.getTipoOperacion()) || !hash.equals(entity.getHashParametros())) {
            throw new TokenMarketException(TokenMarketException.Reason.IDEMPOTENCY_CONFLICT,
                    "La clave de idempotencia ya fue utilizada con otros parametros.");
        }
        return toResult(entity);
    }

    private void validarIdentidadYClave(MarketIdempotencyRequest request) {
        if (request == null || request.usuarioId() == null || request.claveIdempotencia() == null
                || request.claveIdempotencia().isBlank()
                || request.claveIdempotencia().trim().length() > 200) {
            throw new TokenMarketException(TokenMarketException.Reason.INVALID_INPUT,
                    "La identidad y la clave de idempotencia son obligatorias.");
        }
    }

    private String normalizeOperation(String operation) {
        if (operation == null || operation.isBlank()) {
            throw new TokenMarketException(TokenMarketException.Reason.INVALID_INPUT,
                    "El tipo de operacion es obligatorio.");
        }
        String normalized = operation.trim().toUpperCase(Locale.ROOT);
        if (!"PURCHASE".equals(normalized) && !"SALE".equals(normalized)) {
            throw new TokenMarketException(TokenMarketException.Reason.INVALID_INPUT,
                    "El tipo de operacion no es valido.");
        }
        return normalized;
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
