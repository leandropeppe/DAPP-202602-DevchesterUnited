package com.devchesterunited.devchesterunitedv2.service.impl.market;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.modelo.market.EstadoMercado;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.AuditoriaCancelacionOfertaP2PEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.EstadoMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.OfertaVentaP2PEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.TenenciaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.AuditoriaCancelacionOfertaP2PRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.EstadoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.OfertaVentaP2PRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.TenenciaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyClaim;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PCancelCommand;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PCancelResult;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketIdempotencyService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2PCancellationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/** Cancela ofertas P2P liberando solo el remanente reservado. */
@Service
public class MarketP2PCancellationServiceImpl implements MarketP2PCancellationService {
    private final MarketIdempotencyService idempotencia;
    private final EstadoMercadoRepository estados;
    private final OfertaVentaP2PRepository ofertas;
    private final TenenciaMercadoRepository tenencias;
    private final AuditoriaCancelacionOfertaP2PRepository auditorias;
    private final Clock clock;

    public MarketP2PCancellationServiceImpl(MarketIdempotencyService idempotencia,
            EstadoMercadoRepository estados, OfertaVentaP2PRepository ofertas,
            TenenciaMercadoRepository tenencias,
            AuditoriaCancelacionOfertaP2PRepository auditorias, Clock clock) {
        this.idempotencia = idempotencia;
        this.estados = estados;
        this.ofertas = ofertas;
        this.tenencias = tenencias;
        this.auditorias = auditorias;
        this.clock = clock;
    }

    @Override
    @Transactional
    public MarketP2PCancelResult cancelar(MarketP2PCancelCommand command) {
        validarCommand(command);
        MarketIdempotencyClaim claim = idempotencia.reclamarConPropiedad(command);
        MarketIdempotencyResult claimed = claim.resultado();
        if (!claim.propietarioEjecucion()) {
            return resolverReplay(command, claimed);
        }

        validarMercadoOperativo();
        OfertaVentaP2PEntity oferta = ofertas.findByIdAndVendedorIdForUpdate(
                        command.offerId(), command.usuarioId())
                .orElseThrow(() -> new TokenMarketException(
                        TokenMarketException.Reason.OFFER_NOT_FOUND,
                        "La oferta no existe o no pertenece al usuario."));
        validarEstadoCancelable(oferta);

        Instant momento = clock.instant();
        TenenciaMercadoEntity tenencia = tenencias.findByUsuarioIdAndJugadorIdForUpdate(
                        oferta.getVendedorId(), oferta.getJugadorId())
                .orElseThrow(() -> new TokenMarketException(
                        TokenMarketException.Reason.ACCOUNT_NOT_FOUND,
                        "La tenencia del vendedor no existe."));
        long liberada = oferta.getCantidadRestante();
        tenencia.liberarReserva(liberada, momento);
        oferta.actualizarCantidades(0L, oferta.getCantidadVendida(),
                oferta.getCantidadLiberada() + liberada, OfertaVentaP2PEntity.CANCELLED,
                momento);
        assertReservation(oferta, tenencia);

        AuditoriaCancelacionOfertaP2PEntity auditoria = new AuditoriaCancelacionOfertaP2PEntity(
                new AuditoriaCancelacionOfertaP2PEntity.Identidad(
                        java.util.UUID.randomUUID(), oferta.getId(), command.usuarioId(),
                        claimed.solicitudId()),
                new AuditoriaCancelacionOfertaP2PEntity.Cantidades(
                        oferta.getCantidadOriginal(), oferta.getCantidadVendida(),
                        oferta.getCantidadLiberada()),
                momento);
        auditorias.saveAndFlush(auditoria);

        MarketIdempotencyResult confirmed = idempotencia.confirmar(claimed.solicitudId(),
                claimed.tokenEjecucion(), auditoria.getId());
        if (confirmed.estado() != MarketIdempotencyStatus.CONFIRMED
                || !auditoria.getId().equals(confirmed.referenciaResultado())) {
            throw new TokenMarketException(TokenMarketException.Reason.STALE_REQUEST,
                    "La cancelacion P2P no tiene un resultado confirmable.");
        }
        return result(auditoria);
    }

    private MarketP2PCancelResult resolverReplay(MarketP2PCancelCommand command,
            MarketIdempotencyResult result) {
        if (result.estado() == MarketIdempotencyStatus.CONFIRMED
                && result.referenciaResultado() != null) {
            return auditorias.findById(result.referenciaResultado())
                    .filter(auditoria -> command.usuarioId().equals(auditoria.getActorId()))
                    .filter(auditoria -> command.offerId().equals(auditoria.getOfertaId()))
                    .map(this::result)
                    .orElseThrow(this::staleRequest);
        }
        return new MarketP2PCancelResult(command.offerId(), null, null, null,
                result.estado().name());
    }

    private MarketP2PCancelResult result(AuditoriaCancelacionOfertaP2PEntity auditoria) {
        return new MarketP2PCancelResult(auditoria.getOfertaId(),
                auditoria.getCantidadOriginal(), auditoria.getCantidadVendida(),
                auditoria.getCantidadLiberada(), OfertaVentaP2PEntity.CANCELLED);
    }

    private void validarMercadoOperativo() {
        EstadoMercadoEntity estado = estados.findSingletonForRead()
                .orElseThrow(() -> new TokenMarketException(
                        TokenMarketException.Reason.MARKET_NOT_READY,
                        "El mercado no esta operativo."));
        if (!EstadoMercado.READY.name().equals(estado.getStatus())) {
            throw new TokenMarketException(TokenMarketException.Reason.MARKET_NOT_READY,
                    "El mercado no esta operativo.");
        }
    }

    private void validarEstadoCancelable(OfertaVentaP2PEntity oferta) {
        validarOfertaNoCancelada(oferta);
        validarOfertaNoCompletada(oferta);
        validarOfertaAbiertaParaCancelacion(oferta);
    }

    private void validarOfertaNoCancelada(OfertaVentaP2PEntity oferta) {
        if (OfertaVentaP2PEntity.CANCELLED.equals(oferta.getEstado())) {
            throw new TokenMarketException(TokenMarketException.Reason.OFFER_ALREADY_CANCELLED,
                    "La oferta ya fue cancelada.");
        }
    }

    private void validarOfertaNoCompletada(OfertaVentaP2PEntity oferta) {
        if (OfertaVentaP2PEntity.FILLED.equals(oferta.getEstado())) {
            throw new TokenMarketException(TokenMarketException.Reason.OFFER_NOT_PURCHASABLE,
                    "La oferta ya fue completada.");
        }
    }

    private void validarOfertaAbiertaParaCancelacion(OfertaVentaP2PEntity oferta) {
        if (!OfertaVentaP2PEntity.OPEN.equals(oferta.getEstado())
                && !OfertaVentaP2PEntity.PARTIALLY_FILLED.equals(oferta.getEstado())) {
            throw new TokenMarketException(TokenMarketException.Reason.OFFER_NOT_PURCHASABLE,
                    "La oferta no puede cancelarse.");
        }
    }

    private void assertReservation(OfertaVentaP2PEntity oferta,
            TenenciaMercadoEntity tenencia) {
        Long active = ofertas.sumRemainingActiveByVendedorIdAndJugadorId(
                oferta.getVendedorId(), oferta.getJugadorId());
        if (!tenencia.getCantidadReservada().equals(active)) {
            throw new IllegalStateException("La reserva no coincide con las ofertas activas.");
        }
    }

    private static void validarCommand(MarketP2PCancelCommand command) {
        if (command == null || command.usuarioId() == null || command.offerId() == null
                || command.claveIdempotencia() == null
                || command.claveIdempotencia().isBlank()
                || command.claveIdempotencia().trim().length() > 200) {
            throw new TokenMarketException(TokenMarketException.Reason.INVALID_INPUT,
                    "La solicitud de cancelacion P2P no es valida.");
        }
    }

    private TokenMarketException staleRequest() {
        return new TokenMarketException(TokenMarketException.Reason.STALE_REQUEST,
                "El resultado confirmado no esta disponible.");
    }
}
