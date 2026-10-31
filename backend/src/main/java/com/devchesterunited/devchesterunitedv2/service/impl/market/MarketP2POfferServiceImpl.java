package com.devchesterunited.devchesterunitedv2.service.impl.market;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.OfertaVentaP2PEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.OfertaVentaP2PRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyClaim;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2POfferCommand;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2POfferResult;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketIdempotencyService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2POfferService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** Orquesta el claim, la validación oficial y la reserva de una oferta P2P. */
@Service
public class MarketP2POfferServiceImpl implements MarketP2POfferService {
    private static final String OPERATION = "P2P_OFFER";

    private final MarketIdempotencyService idempotencia;
    private final MarketP2POfferValidationService validacion;
    private final MarketP2POfferPersistenceServiceImpl persistencia;
    private final OfertaVentaP2PRepository ofertas;
    private final Clock clock;

    public MarketP2POfferServiceImpl(MarketIdempotencyService idempotencia,
            MarketP2POfferValidationService validacion,
            MarketP2POfferPersistenceServiceImpl persistencia,
            OfertaVentaP2PRepository ofertas, Clock clock) {
        this.idempotencia = idempotencia;
        this.validacion = validacion;
        this.persistencia = persistencia;
        this.ofertas = ofertas;
        this.clock = clock;
    }

    @Override
    @Transactional
    public MarketP2POfferResult publicar(MarketP2POfferCommand command) {
        validarCommand(command);
        MarketIdempotencyClaim claim = idempotencia.reclamarConPropiedad(
                new MarketIdempotencyRequest(command.usuarioId(), command.claveIdempotencia(),
                        OPERATION, command.jugadorId(), command.cantidad(), null, null));
        MarketIdempotencyResult result = claim.resultado();
        if (!claim.propietarioEjecucion()) {
            return resolverReplay(command, result);
        }

        validacion.validar(command.jugadorId());
        OfertaVentaP2PEntity oferta = persistencia.persistir(result.solicitudId(),
                command.usuarioId(), command.jugadorId(), command.cantidad(), clock.instant());
        MarketIdempotencyResult confirmed = idempotencia.confirmar(result.solicitudId(),
                result.tokenEjecucion(), oferta.getId());
        if (confirmed.estado() != MarketIdempotencyStatus.CONFIRMED
                || !oferta.getId().equals(confirmed.referenciaResultado())) {
            throw new TokenMarketException(TokenMarketException.Reason.STALE_REQUEST,
                    "La solicitud de oferta ya no tiene un resultado confirmable.");
        }
        return toResult(oferta);
    }

    private MarketP2POfferResult resolverReplay(MarketP2POfferCommand command,
            MarketIdempotencyResult result) {
        if (result.estado() == MarketIdempotencyStatus.CONFIRMED
                && result.referenciaResultado() != null) {
            return ofertas.findById(result.referenciaResultado())
                    .map(this::toResult)
                    .orElseThrow(() -> new TokenMarketException(
                            TokenMarketException.Reason.STALE_REQUEST,
                            "El resultado confirmado no esta disponible."));
        }
        return new MarketP2POfferResult(result.referenciaResultado(), command.usuarioId(),
                command.jugadorId(), null, result.estado().name());
    }

    private MarketP2POfferResult toResult(OfertaVentaP2PEntity oferta) {
        return new MarketP2POfferResult(oferta.getId(), oferta.getVendedorId(),
                oferta.getJugadorId(), new MarketP2POfferResult.Quantities(
                        oferta.getCantidadOriginal(), oferta.getCantidadRestante(),
                        oferta.getCantidadRestante()), oferta.getEstado());
    }

    private static void validarCommand(MarketP2POfferCommand command) {
        if (command == null || command.usuarioId() == null || command.jugadorId() == null
                || command.cantidad() == null || command.cantidad() <= 0
                || command.claveIdempotencia() == null
                || command.claveIdempotencia().isBlank()
                || command.claveIdempotencia().trim().length() > 200) {
            throw new TokenMarketException(TokenMarketException.Reason.INVALID_INPUT,
                    "La solicitud de oferta no es valida.");
        }
    }
}
