package com.devchesterunited.devchesterunitedv2.service.impl.market;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.modelo.market.EstadoMercado;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.CabeceraCotizacionMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.EstadoMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.OfertaVentaP2PEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.VersionCotizacionMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.CabeceraCotizacionMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.EstadoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.OfertaVentaP2PRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.VersionCotizacionMercadoRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseCommand;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseSettlement;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseTarget;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PQuoteIdentity;
import com.devchesterunited.devchesterunitedv2.modelo.market.QuoteValidityStatus;
import com.devchesterunited.devchesterunitedv2.modelo.market.QuoteVersion;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/** Adquiere y revalida el prefijo de locks de una compra P2P. */
@Service
public class MarketP2PPurchaseValidationService {
    private static final String VALID_QUOTE_STATUS = QuoteValidityStatus.VALID.name();

    private final EstadoMercadoRepository estados;
    private final OfertaVentaP2PRepository ofertas;
    private final JugadorRepository jugadores;
    private final CabeceraCotizacionMercadoRepository cabeceras;
    private final VersionCotizacionMercadoRepository versiones;
    private final Clock clock;

    public MarketP2PPurchaseValidationService(EstadoMercadoRepository estados,
            OfertaVentaP2PRepository ofertas, JugadorRepository jugadores,
            CabeceraCotizacionMercadoRepository cabeceras,
            VersionCotizacionMercadoRepository versiones, Clock clock) {
        this.estados = estados;
        this.ofertas = ofertas;
        this.jugadores = jugadores;
        this.cabeceras = cabeceras;
        this.versiones = versiones;
        this.clock = clock;
    }

    public MarketP2PPurchaseSettlement validar(MarketP2PPurchaseCommand command,
            java.util.UUID requestId) {
        EstadoMercadoEntity estado = estados.findSingletonForRead()
                .orElseThrow(this::marketNotReady);
        validarMercadoOperativo(estado);
        OfertaVentaP2PEntity offer = ofertas.findByIdForUpdate(command.offerId())
                .orElseThrow(this::offerNotPurchasable);
        validarOfertaAbierta(offer);
        validarJugadorDeOferta(command, offer);
        validarCompradorDistintoDelVendedor(command, offer);
        validarCantidadDisponible(command, offer);

        JugadorEntity player = jugadores.findByIdForUpdate(command.playerId())
                .orElseThrow(this::inactivePlayer);
        validarJugadorActivo(player);
        CabeceraCotizacionMercadoEntity header = cabeceras
                .findByJugadorIdForUpdate(command.playerId())
                .orElseThrow(this::quoteUnavailable);
        Instant now = clock.instant();
        QuoteVersion quote = currentQuote(command.playerId(), header);
        validarIdentidadDeCotizacion(command, header, quote);
        validarCotizacionDisponible(quote);
        validarCotizacionVigente(quote, now);
        MarketP2PPurchaseTarget target = new MarketP2PPurchaseTarget(
                offer.getId(), player.getId(), player.getNombre());
        return new MarketP2PPurchaseSettlement(requestId, command.usuarioId(), target,
                quote, command.quantity(), now);
    }

    private void validarMercadoOperativo(EstadoMercadoEntity estado) {
        if (!EstadoMercado.READY.name().equals(estado.getStatus())) {
            throw marketNotReady();
        }
    }

    private void validarOfertaAbierta(OfertaVentaP2PEntity offer) {
        if (!OfertaVentaP2PEntity.OPEN.equals(offer.getEstado())
                && !OfertaVentaP2PEntity.PARTIALLY_FILLED.equals(offer.getEstado())) {
            throw offerNotPurchasable();
        }
    }

    private void validarJugadorDeOferta(MarketP2PPurchaseCommand command,
            OfertaVentaP2PEntity offer) {
        if (!command.playerId().equals(offer.getJugadorId())) {
            throw new TokenMarketException(TokenMarketException.Reason.INVALID_INPUT,
                    "La oferta no corresponde al jugador solicitado.");
        }
    }

    private void validarCompradorDistintoDelVendedor(MarketP2PPurchaseCommand command,
            OfertaVentaP2PEntity offer) {
        if (command.usuarioId().equals(offer.getVendedorId())) {
            throw new TokenMarketException(TokenMarketException.Reason.OFFER_NOT_PURCHASABLE,
                    "El vendedor no puede comprar su propia oferta.");
        }
    }

    private void validarCantidadDisponible(MarketP2PPurchaseCommand command,
            OfertaVentaP2PEntity offer) {
        if (command.quantity() > offer.getCantidadRestante()) {
            throw new TokenMarketException(TokenMarketException.Reason.INSUFFICIENT_AVAILABLE_QUANTITY,
                    "La cantidad solicitada supera el remanente de la oferta.");
        }
    }

    private void validarJugadorActivo(JugadorEntity player) {
        if (!player.isActivo()) {
            throw inactivePlayer();
        }
    }

    private void validarIdentidadDeCotizacion(MarketP2PPurchaseCommand command,
            CabeceraCotizacionMercadoEntity header, QuoteVersion quote) {
        if (!sameQuote(command, header)) {
            throw quoteChanged(quote);
        }
    }

    private void validarCotizacionDisponible(QuoteVersion quote) {
        if (quote == null) {
            throw quoteUnavailable();
        }
    }

    private void validarCotizacionVigente(QuoteVersion quote, Instant now) {
        if (!quote.isValidAt(now)) {
            throw quoteUnavailable();
        }
    }

    public java.util.UUID resolverJugadorId(java.util.UUID offerId) {
        return ofertas.findById(offerId).map(OfertaVentaP2PEntity::getJugadorId)
                .orElseThrow(() -> new TokenMarketException(
                        TokenMarketException.Reason.OFFER_NOT_FOUND, "La oferta no existe."));
    }

    private QuoteVersion currentQuote(java.util.UUID playerId,
            CabeceraCotizacionMercadoEntity header) {
        if (!VALID_QUOTE_STATUS.equals(header.getEstadoVigencia())) {
            return null;
        }
        VersionCotizacionMercadoEntity entity = versiones.findExact(
                playerId, header.getQuoteId(), header.getVersionActual()).orElse(null);
        if (entity == null || !VALID_QUOTE_STATUS.equals(entity.getEstadoVigencia())) {
            return null;
        }
        return new QuoteVersion(entity.getId().getQuoteId(), entity.getId().getVersion(),
                entity.getJugadorId(), entity.getPrecioPorToken(), entity.getPublicadoEn(),
                entity.getValidoDesde(), entity.getValidoHasta(),
                QuoteValidityStatus.valueOf(entity.getEstadoVigencia()),
                entity.getReferenciaEstrategia());
    }

    private boolean sameQuote(MarketP2PPurchaseCommand command,
            CabeceraCotizacionMercadoEntity header) {
        MarketP2PQuoteIdentity observed = command.quote();
        return observed.quoteId().equals(header.getQuoteId())
                && observed.version().equals(header.getVersionActual())
                && VALID_QUOTE_STATUS.equals(header.getEstadoVigencia());
    }

    private TokenMarketException marketNotReady() {
        return new TokenMarketException(TokenMarketException.Reason.MARKET_NOT_READY,
                "El mercado no esta operativo.");
    }

    private TokenMarketException offerNotPurchasable() {
        return new TokenMarketException(TokenMarketException.Reason.OFFER_NOT_PURCHASABLE,
                "La oferta no esta disponible para compra.");
    }

    private TokenMarketException inactivePlayer() {
        return new TokenMarketException(TokenMarketException.Reason.INACTIVE_PLAYER,
                "El jugador no esta activo.");
    }

    private TokenMarketException quoteUnavailable() {
        return new TokenMarketException(TokenMarketException.Reason.QUOTE_UNAVAILABLE,
                "La cotizacion oficial no esta vigente.");
    }

    private TokenMarketException quoteChanged(QuoteVersion currentQuote) {
        return new TokenMarketException(TokenMarketException.Reason.QUOTE_CHANGED,
                "La cotizacion observada ya no es la vigente.", currentQuote);
    }
}
