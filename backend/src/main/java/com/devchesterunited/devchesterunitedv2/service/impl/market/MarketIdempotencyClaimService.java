package com.devchesterunited.devchesterunitedv2.service.impl.market;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.SolicitudIdempotenciaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.SolicitudIdempotenciaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyClaim;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PCancelCommand;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PIdempotencyRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Reclama solicitudes idempotentes dentro del proxy transaccional de Spring. */
@Service
public class MarketIdempotencyClaimService {
    private final SolicitudIdempotenciaMercadoRepository solicitudes;
    private final Clock clock;
    private final Duration processingTimeout;
    private final MarketOperationExecutionLock executionLock;

    @Autowired
    public MarketIdempotencyClaimService(SolicitudIdempotenciaMercadoRepository solicitudes,
            Clock clock, MarketOperationExecutionLock executionLock) {
        this(solicitudes, clock, Duration.ofMinutes(5), executionLock);
    }

    public MarketIdempotencyClaimService(SolicitudIdempotenciaMercadoRepository solicitudes,
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

    @Transactional
    public MarketIdempotencyClaim reclamarConPropiedad(MarketIdempotencyRequest request) {
        validarIdentidadYClave(request);
        String normalizedKey = request.claveIdempotencia().trim();
        String operation = normalizeOperation(request.tipoOperacion());
        String hash = MarketIdempotencyParameterHasher.hash(request);
        return reclamarInterno(request.usuarioId(), normalizedKey, operation, hash);
    }

    @Transactional
    public MarketIdempotencyClaim reclamarConPropiedad(MarketP2PIdempotencyRequest request) {
        request.validar();
        String normalizedKey = request.claveIdempotencia().trim();
        String operation = "P2P_PURCHASE";
        String hash = MarketIdempotencyParameterHasher.hash(request);
        return reclamarInterno(request.usuarioId(), normalizedKey, operation, hash);
    }

    @Transactional
    public MarketIdempotencyClaim reclamarConPropiedad(MarketP2PCancelCommand request) {
        if (request == null || request.usuarioId() == null || request.offerId() == null
                || request.claveIdempotencia() == null || request.claveIdempotencia().isBlank()
                || request.claveIdempotencia().trim().length() > 200) {
            throw new TokenMarketException(TokenMarketException.Reason.INVALID_INPUT,
                    "La solicitud de cancelacion P2P no es valida.");
        }
        String normalizedKey = request.claveIdempotencia().trim();
        return reclamarInterno(request.usuarioId(), normalizedKey, "P2P_CANCEL",
                MarketIdempotencyParameterHasher.hash(request));
    }

    private MarketIdempotencyClaim reclamarInterno(UUID usuarioId, String normalizedKey,
            String operation, String hash) {
        Instant now = clock.instant();

        if (!executionLock.tryAcquire(usuarioId, normalizedKey)) {
            MarketIdempotencyResult result = solicitudes
                    .findByUsuarioIdAndClaveIdempotencia(usuarioId, normalizedKey)
                    .map(entity -> recoverWhileBusy(entity, operation, hash))
                    .orElseGet(() -> pendiente(usuarioId, normalizedKey, operation, hash, now));
            return new MarketIdempotencyClaim(result, false);
        }

        Optional<SolicitudIdempotenciaMercadoEntity> existing =
                solicitudes.findByUsuarioIdAndClaveForUpdate(usuarioId, normalizedKey);
        if (existing.isPresent()) {
            SolicitudIdempotenciaMercadoEntity entity = existing.get();
            if (!operation.equals(entity.getTipoOperacion()) || !hash.equals(entity.getHashParametros())) {
                throw new TokenMarketException(TokenMarketException.Reason.IDEMPOTENCY_CONFLICT,
                        "La clave de idempotencia ya fue utilizada con otros parametros.");
            }
            if (SolicitudIdempotenciaMercadoEntity.STATUS_PROCESSING.equals(entity.getStatus())
                    && !now.isBefore(entity.getActualizadoEn().plus(processingTimeout))) {
                entity.reclamarNuevamente(UUID.randomUUID(), now);
                return new MarketIdempotencyClaim(toResult(entity), true);
            }
            return new MarketIdempotencyClaim(toResult(entity), false);
        }

        UUID executionToken = UUID.randomUUID();
        SolicitudIdempotenciaMercadoEntity entity = new SolicitudIdempotenciaMercadoEntity(
                UUID.randomUUID(), usuarioId, normalizedKey, operation, hash,
                SolicitudIdempotenciaMercadoEntity.STATUS_PROCESSING, executionToken, null, now, now);
        try {
            return new MarketIdempotencyClaim(toResult(solicitudes.saveAndFlush(entity)), true);
        } catch (DataIntegrityViolationException exception) {
            throw new TokenMarketException(TokenMarketException.Reason.IDEMPOTENCY_CONFLICT,
                    "La solicitud de idempotencia ya existe.");
        }
    }

    private MarketIdempotencyResult pendiente(UUID usuarioId, String normalizedKey,
            String operation, String hash, Instant now) {
        return new MarketIdempotencyResult(null, usuarioId, normalizedKey, operation, hash,
                com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyStatus.PROCESSING,
                null, null, now);
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
        if (!"PURCHASE".equals(normalized) && !"SALE".equals(normalized)
                && !"P2P_OFFER".equals(normalized) && !"P2P_PURCHASE".equals(normalized)
                && !"P2P_CANCEL".equals(normalized)) {
            throw new TokenMarketException(TokenMarketException.Reason.INVALID_INPUT,
                    "El tipo de operacion no es valido.");
        }
        return normalized;
    }

    private MarketIdempotencyResult toResult(SolicitudIdempotenciaMercadoEntity entity) {
        return new MarketIdempotencyResult(entity.getId(), entity.getUsuarioId(),
                entity.getClaveIdempotencia(), entity.getTipoOperacion(), entity.getHashParametros(),
                com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyStatus
                        .valueOf(entity.getStatus()), entity.getTokenEjecucion(),
                entity.getReferenciaResultado(), entity.getActualizadoEn());
    }
}
