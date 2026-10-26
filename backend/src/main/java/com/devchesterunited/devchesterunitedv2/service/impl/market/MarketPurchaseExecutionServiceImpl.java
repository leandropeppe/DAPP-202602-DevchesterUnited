package com.devchesterunited.devchesterunitedv2.service.impl.market;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.modelo.market.EstadoMercado;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.ContraparteMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.EstadoMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.CabeceraCotizacionMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.ContraparteMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.EstadoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.market.*;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Liquida y confirma una compra dentro de un único límite transaccional. */
@Service
public class MarketPurchaseExecutionServiceImpl implements MarketPurchaseExecutionService {
    private static final String PURCHASE = "PURCHASE";
    private static final String SUPERUSER = "SUPERUSER";

    private final MarketIdempotencyService idempotencia;
    private final MarketIdempotencyExecutionPort executionGuard;
    private final EstadoMercadoRepository estados;
    private final JugadorRepository jugadores;
    private final CabeceraCotizacionMercadoRepository cabeceras;
    private final MarketQuoteValidationService cotizaciones;
    private final ContraparteMercadoRepository contrapartes;
    private final MarketPurchasePersistenceService persistencia;
    private final Clock clock;

    public MarketPurchaseExecutionServiceImpl(MarketIdempotencyService idempotencia,
            EstadoMercadoRepository estados, JugadorRepository jugadores,
            CabeceraCotizacionMercadoRepository cabeceras,
            MarketQuoteValidationService cotizaciones,
            ContraparteMercadoRepository contrapartes,
            MarketPurchasePersistenceService persistencia, Clock clock) {
        this.idempotencia = idempotencia;
        this.executionGuard = idempotencia instanceof MarketIdempotencyExecutionPort guard
                ? guard : null;
        this.estados = estados;
        this.jugadores = jugadores;
        this.cabeceras = cabeceras;
        this.cotizaciones = cotizaciones;
        this.contrapartes = contrapartes;
        this.persistencia = persistencia;
        this.clock = clock;
    }

    @Override
    @Transactional
    public MarketPurchaseResult ejecutar(MarketPurchaseRequest request,
            MarketIdempotencyClaim claim) {
        MarketIdempotencyResult claimed = claim.resultado();
        if (executionGuard != null) {
            executionGuard.asegurarPropiedadDeEjecucion(claimed.solicitudId(),
                    claimed.tokenEjecucion());
        }
        EstadoMercadoEntity estado = estados.findSingletonForRead()
                .orElseThrow(this::mercadoNoOperativo);
        if (!EstadoMercado.READY.name().equals(estado.getStatus())) {
            throw mercadoNoOperativo();
        }
        JugadorEntity jugador = jugadores.findByIdForRead(request.jugadorId())
                .orElseThrow(() -> new TokenMarketException(
                        TokenMarketException.Reason.INACTIVE_PLAYER,
                        "El jugador no esta activo."));
        if (!jugador.isActivo()) {
            throw new TokenMarketException(TokenMarketException.Reason.INACTIVE_PLAYER,
                    "El jugador no esta activo.");
        }

        cabeceras.findByJugadorIdForUpdate(request.jugadorId());
        QuoteVersion quote = cotizaciones.validar(request.jugadorId(), request.cotizacionId(),
                request.versionCotizacion());
        Instant operacionEn = clock.instant();
        if (quote == null || !request.cotizacionId().equals(quote.quoteId())
                || request.versionCotizacion().longValue() != quote.version()
                || !request.jugadorId().equals(quote.playerId())
                || !quote.isValidAt(operacionEn)) {
            throw new TokenMarketException(TokenMarketException.Reason.QUOTE_UNAVAILABLE,
                    "La cotizacion exacta ya no esta vigente.");
        }
        BigDecimal total = calcularTotal(quote.pricePerToken(), request.cantidad());
        ContraparteMercadoEntity superuser = contrapartes.findByTipoContraparte(SUPERUSER)
                .orElseThrow(() -> new TokenMarketException(
                        TokenMarketException.Reason.ACCOUNT_NOT_FOUND,
                        "La contraparte SUPERUSER no existe."));
        UUID transaccionId = persistencia.persistirCompra(new MarketPurchaseSettlement(
                claimed.solicitudId(), request.usuarioId(), request.jugadorId(),
                jugador.getNombre(), superuser.getId(), quote.quoteId(), quote.version(),
                request.cantidad(), quote.pricePerToken(), total, operacionEn));
        MarketIdempotencyResult confirmed = idempotencia.confirmar(claimed.solicitudId(),
                claimed.tokenEjecucion(), transaccionId);
        if (confirmed.estado() != MarketIdempotencyStatus.CONFIRMED
                || !transaccionId.equals(confirmed.referenciaResultado())) {
            throw new TokenMarketException(TokenMarketException.Reason.STALE_REQUEST,
                    "La solicitud de compra ya no tiene un resultado confirmable.");
        }
        return new MarketPurchaseResult(claimed.solicitudId(), transaccionId,
                request.usuarioId(), request.jugadorId(), request.cantidad(),
                quote.pricePerToken(), total, MarketIdempotencyStatus.CONFIRMED,
                confirmed.actualizadoEn());
    }

    private static BigDecimal calcularTotal(BigDecimal precio, Long cantidad) {
        try {
            BigDecimal total = precio.multiply(BigDecimal.valueOf(cantidad))
                    .setScale(2, RoundingMode.UNNECESSARY);
            if (total.precision() - total.scale() > 18) {
                throw new ArithmeticException("El importe excede la precision persistible.");
            }
            return total;
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new TokenMarketException(TokenMarketException.Reason.INVALID_INPUT,
                    "El importe de la compra no es representable.");
        }
    }

    private TokenMarketException mercadoNoOperativo() {
        return new TokenMarketException(TokenMarketException.Reason.MARKET_NOT_READY,
                "El mercado no esta operativo.");
    }
}
