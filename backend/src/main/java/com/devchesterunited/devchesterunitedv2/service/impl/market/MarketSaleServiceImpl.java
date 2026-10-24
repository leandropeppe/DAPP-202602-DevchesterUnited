package com.devchesterunited.devchesterunitedv2.service.impl.market;

import com.devchesterunited.devchesterunitedv2.persistence.market.repository.CabeceraCotizacionMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.ContraparteMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.EstadoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.TransaccionConfirmadaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyClaim;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketRejectedAttempt;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketSaleRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketSaleResult;
import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketIdempotencyService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketQuoteValidationService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketRejectionFinalizationService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketSaleExecutionService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketSalePersistenceService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketSaleService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Fachada no transaccional: reclama, ejecuta y finaliza rechazos después del rollback. */
@Service
public class MarketSaleServiceImpl implements MarketSaleService {
    private static final String SALE = "SALE";
    private static final long MAX_TOKENS_PER_SALE = 100L;

    private final MarketIdempotencyService idempotencia;
    private final MarketSaleExecutionService execution;
    private final MarketRejectionFinalizationService rejectionFinalization;
    private final TransaccionConfirmadaMercadoRepository transacciones;
    private final Clock clock;

    @Autowired
    public MarketSaleServiceImpl(MarketIdempotencyService idempotencia,
            MarketSaleExecutionService execution,
            MarketRejectionFinalizationService rejectionFinalization,
            TransaccionConfirmadaMercadoRepository transacciones, Clock clock) {
        this.idempotencia = idempotencia;
        this.execution = execution;
        this.rejectionFinalization = rejectionFinalization;
        this.transacciones = transacciones;
        this.clock = clock;
    }

    /** Compatibilidad de construcción para las pruebas unitarias existentes. */
    public MarketSaleServiceImpl(MarketIdempotencyService idempotencia,
            EstadoMercadoRepository estados, JugadorRepository jugadores,
            CabeceraCotizacionMercadoRepository cabeceras,
            MarketQuoteValidationService cotizaciones,
            ContraparteMercadoRepository contrapartes,
            MarketSalePersistenceService persistencia,
            TransaccionConfirmadaMercadoRepository transacciones, Clock clock) {
        this(idempotencia,
                new MarketSaleExecutionServiceImpl(idempotencia, estados, jugadores, cabeceras,
                        cotizaciones, contrapartes, persistencia, clock),
                (solicitudId, token, attempt) -> null, transacciones, clock);
    }

    @Override
    public MarketSaleResult vender(MarketSaleRequest request) {
        validarSolicitud(request);
        MarketIdempotencyClaim claim = idempotencia.reclamarConPropiedad(
                new MarketIdempotencyRequest(request.usuarioId(), request.claveIdempotencia(),
                        SALE, request.jugadorId(), request.cantidad(), request.cotizacionId(),
                        request.versionCotizacion()));
        MarketIdempotencyResult claimed = claim.resultado();
        if (!claim.propietarioEjecucion()) {
            return resolverNoPropietario(request, claimed);
        }
        if (claimed.solicitudId() == null || claimed.tokenEjecucion() == null) {
            return pending(request, claimed);
        }

        try {
            return execution.ejecutar(request, claim);
        } catch (TokenMarketException failure) {
            if (esRechazoDefinitivo(failure.reason())) {
                finalizarRechazo(request, claimed, failure);
            }
            throw failure;
        }
    }

    private void finalizarRechazo(MarketSaleRequest request,
            MarketIdempotencyResult claimed, TokenMarketException failure) {
        MarketRejectedAttempt attempt = new MarketRejectedAttempt(request.usuarioId(), SALE,
                request.jugadorId(), hash(request.claveIdempotencia()), failure.reason().name(),
                claimed.hashParametros(), clock.instant());
        try {
            rejectionFinalization.finalizarRechazo(claimed.solicitudId(),
                    claimed.tokenEjecucion(), attempt);
        } catch (RuntimeException ignored) {
            // El error funcional original sigue siendo el contrato HTTP observable.
        }
    }

    private boolean esRechazoDefinitivo(TokenMarketException.Reason reason) {
        return reason == TokenMarketException.Reason.INSUFFICIENT_BALANCE
                || reason == TokenMarketException.Reason.INSUFFICIENT_INVENTORY
                || reason == TokenMarketException.Reason.INACTIVE_PLAYER
                || reason == TokenMarketException.Reason.QUOTE_UNAVAILABLE
                || reason == TokenMarketException.Reason.MARKET_NOT_READY
                || reason == TokenMarketException.Reason.ACCOUNT_NOT_FOUND;
    }

    private MarketSaleResult resolverNoPropietario(MarketSaleRequest request,
            MarketIdempotencyResult result) {
        if (result.estado() == MarketIdempotencyStatus.CONFIRMED) {
            if (result.referenciaResultado() == null) {
                throw new TokenMarketException(TokenMarketException.Reason.STALE_REQUEST,
                        "La operacion confirmada no tiene referencia durable.");
            }
            var tx = transacciones.findByIdAndUsuarioId(result.referenciaResultado(),
                    request.usuarioId()).orElseThrow(() -> new TokenMarketException(
                            TokenMarketException.Reason.STALE_REQUEST,
                            "El resultado confirmado no esta disponible."));
            return new MarketSaleResult(tx.getSolicitudIdempotenciaId(), tx.getId(),
                    tx.getUsuarioId(), tx.getJugadorId(), tx.getQuantity(), tx.getPrecioUnitario(),
                    tx.getMontoTotal(), MarketIdempotencyStatus.CONFIRMED, tx.getConfirmadoEn());
        }
        return new MarketSaleResult(result.solicitudId(), null, request.usuarioId(),
                request.jugadorId(), request.cantidad(), null, null, result.estado(),
                result.actualizadoEn());
    }

    private MarketSaleResult pending(MarketSaleRequest request, MarketIdempotencyResult result) {
        return new MarketSaleResult(result.solicitudId(), null, request.usuarioId(),
                request.jugadorId(), request.cantidad(), null, null, result.estado(),
                result.actualizadoEn());
    }

    private static void validarSolicitud(MarketSaleRequest request) {
        if (request == null || request.usuarioId() == null || request.jugadorId() == null
                || request.cotizacionId() == null || request.versionCotizacion() == null
                || request.versionCotizacion() <= 0 || request.cantidad() == null
                || request.cantidad() <= 0 || request.cantidad() > MAX_TOKENS_PER_SALE
                || request.claveIdempotencia() == null || request.claveIdempotencia().isBlank()
                || request.claveIdempotencia().trim().length() > 200) {
            throw new TokenMarketException(TokenMarketException.Reason.INVALID_INPUT,
                    "La solicitud de venta no es valida.");
        }
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) {
                result.append(String.format(Locale.ROOT, "%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no esta disponible en la JVM.", exception);
        }
    }
}
