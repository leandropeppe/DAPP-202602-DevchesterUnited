package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.persistence.CabeceraCotizacionMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.ContraparteMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.EstadoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.TransaccionConfirmadaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.ContraparteMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EstadoMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.TransaccionConfirmadaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.modelo.EstadoMercado;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketIdempotencyClaim;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketIdempotencyRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketIdempotencyResult;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketIdempotencyStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketSaleRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketSaleResult;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketSaleSettlement;
import com.devchesterunited.devchesterunitedv2.service.dto.QuoteVersion;
import com.devchesterunited.devchesterunitedv2.service.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.interfaces.MarketIdempotencyService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.MarketQuoteValidationService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.MarketSalePersistenceService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.MarketSaleService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orquesta una venta completa dentro de una unica transaccion. */
@Service
public class MarketSaleServiceImpl implements MarketSaleService {
    private static final String SALE = "SALE";
    private static final String SUPERUSER = "SUPERUSER";
    private static final long MAX_TOKENS_PER_SALE = 100L;

    private final MarketIdempotencyService idempotencia;
    private final EstadoMercadoRepository estados;
    private final JugadorRepository jugadores;
    private final CabeceraCotizacionMercadoRepository cabeceras;
    private final MarketQuoteValidationService cotizaciones;
    private final ContraparteMercadoRepository contrapartes;
    private final MarketSalePersistenceService persistencia;
    private final TransaccionConfirmadaMercadoRepository transacciones;
    private final Clock clock;

    public MarketSaleServiceImpl(MarketIdempotencyService idempotencia,
            EstadoMercadoRepository estados, JugadorRepository jugadores,
            CabeceraCotizacionMercadoRepository cabeceras,
            MarketQuoteValidationService cotizaciones,
            ContraparteMercadoRepository contrapartes,
            MarketSalePersistenceService persistencia,
            TransaccionConfirmadaMercadoRepository transacciones, Clock clock) {
        this.idempotencia = idempotencia;
        this.estados = estados;
        this.jugadores = jugadores;
        this.cabeceras = cabeceras;
        this.cotizaciones = cotizaciones;
        this.contrapartes = contrapartes;
        this.persistencia = persistencia;
        this.transacciones = transacciones;
        this.clock = clock;
    }

    @Override
    @Transactional
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

        // Este lock se mantiene por la transaccion externa hasta confirmar la venta.
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

        UUID transaccionId = persistencia.persistirVenta(new MarketSaleSettlement(
                claimed.solicitudId(), request.usuarioId(), request.jugadorId(),
                jugador.getNombre(), superuser.getId(), quote.quoteId(), quote.version(),
                request.cantidad(), quote.pricePerToken(), total, operacionEn));
        MarketIdempotencyResult confirmed = idempotencia.confirmar(claimed.solicitudId(),
                claimed.tokenEjecucion(), transaccionId);
        if (confirmed.estado() != MarketIdempotencyStatus.CONFIRMED
                || !transaccionId.equals(confirmed.referenciaResultado())) {
            throw new TokenMarketException(TokenMarketException.Reason.STALE_REQUEST,
                    "La solicitud de venta ya no tiene un resultado confirmable.");
        }
        return confirmed(request, claimed.solicitudId(), transaccionId, quote, total,
                confirmed.actualizadoEn());
    }

    private MarketSaleResult resolverNoPropietario(MarketSaleRequest request,
            MarketIdempotencyResult result) {
        if (result.estado() == MarketIdempotencyStatus.CONFIRMED) {
            return recuperarConfirmada(request, result);
        }
        return new MarketSaleResult(result.solicitudId(), null, request.usuarioId(),
                request.jugadorId(), request.cantidad(), null, null, result.estado(),
                result.actualizadoEn());
    }

    private MarketSaleResult recuperarConfirmada(MarketSaleRequest request,
            MarketIdempotencyResult result) {
        if (result.referenciaResultado() == null) {
            throw new TokenMarketException(TokenMarketException.Reason.STALE_REQUEST,
                    "La operacion confirmada no tiene referencia durable.");
        }
        TransaccionConfirmadaMercadoEntity tx = transacciones
                .findByIdAndUsuarioId(result.referenciaResultado(), request.usuarioId())
                .orElseThrow(() -> new TokenMarketException(
                        TokenMarketException.Reason.STALE_REQUEST,
                        "El resultado confirmado no esta disponible."));
        return new MarketSaleResult(tx.getSolicitudIdempotenciaId(), tx.getId(),
                tx.getUsuarioId(), tx.getJugadorId(), tx.getQuantity(), tx.getPrecioUnitario(),
                tx.getMontoTotal(), MarketIdempotencyStatus.CONFIRMED, tx.getConfirmadoEn());
    }

    private MarketSaleResult confirmed(MarketSaleRequest request, UUID solicitudId,
            UUID transaccionId, QuoteVersion quote, BigDecimal total, Instant actualizadoEn) {
        return new MarketSaleResult(solicitudId, transaccionId, request.usuarioId(),
                request.jugadorId(), request.cantidad(), quote.pricePerToken(), total,
                MarketIdempotencyStatus.CONFIRMED, actualizadoEn);
    }

    private MarketSaleResult pending(MarketSaleRequest request, MarketIdempotencyResult result) {
        return new MarketSaleResult(result.solicitudId(), null, request.usuarioId(),
                request.jugadorId(), request.cantidad(), null, null, result.estado(),
                result.actualizadoEn());
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
                    "El importe de la venta no es representable.");
        }
    }

    private static void validarSolicitud(MarketSaleRequest request) {
        if (request == null || request.usuarioId() == null || request.jugadorId() == null
                || request.cotizacionId() == null || request.versionCotizacion() == null
                || request.versionCotizacion() <= 0 || request.cantidad() == null
                || request.cantidad() <= 0 || request.cantidad() > MAX_TOKENS_PER_SALE
                || request.claveIdempotencia() == null
                || request.claveIdempotencia().isBlank()
                || request.claveIdempotencia().trim().length() > 200) {
            throw new TokenMarketException(TokenMarketException.Reason.INVALID_INPUT,
                    "La solicitud de venta no es valida.");
        }
    }

    private TokenMarketException mercadoNoOperativo() {
        return new TokenMarketException(TokenMarketException.Reason.MARKET_NOT_READY,
                "El mercado no esta operativo.");
    }
}
