package com.devchesterunited.devchesterunitedv2.service.impl.market;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyClaim;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PIdempotencyRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseCommand;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseSettlement;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PQuoteIdentity;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketIdempotencyService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2PPurchaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Orquesta una compra P2P y deja el settlement en una única transacción. */
@Service
public class MarketP2PPurchaseExecutionServiceImpl implements MarketP2PPurchaseService {
    private final MarketIdempotencyService idempotencia;
    private final MarketP2PPurchaseValidationService validacion;
    private final MarketP2PPurchasePersistenceServiceImpl persistencia;

    public MarketP2PPurchaseExecutionServiceImpl(MarketIdempotencyService idempotencia,
            MarketP2PPurchaseValidationService validacion,
            MarketP2PPurchasePersistenceServiceImpl persistencia) {
        this.idempotencia = idempotencia;
        this.validacion = validacion;
        this.persistencia = persistencia;
    }

    @Override
    @Transactional
    public MarketP2PPurchaseResult comprar(UUID usuarioId, String claveIdempotencia,
            UUID offerId, Long quantity, MarketP2PQuoteIdentity quote) {
        UUID playerId = validacion.resolverJugadorId(offerId);
        return comprar(new MarketP2PPurchaseCommand(usuarioId, claveIdempotencia, offerId,
                playerId, quantity, quote));
    }

    @Override
    @Transactional
    public MarketP2PPurchaseResult comprar(MarketP2PPurchaseCommand command) {
        validar(command);
        MarketIdempotencyClaim claim = idempotencia.reclamarConPropiedad(
                new MarketP2PIdempotencyRequest(command.usuarioId(),
                        command.claveIdempotencia(), command.idempotencyIdentity()));
        MarketIdempotencyResult claimed = claim.resultado();
        if (!claim.propietarioEjecucion()) {
            return replayOrPending(command, claimed);
        }

        MarketP2PPurchaseSettlement settlement = validacion.validar(command,
                claimed.solicitudId());
        MarketP2PPurchaseResult result = persistencia.liquidar(settlement);
        MarketIdempotencyResult confirmed = idempotencia.confirmar(claimed.solicitudId(),
                claimed.tokenEjecucion(), result.tradeId());
        if (confirmed.estado() != MarketIdempotencyStatus.CONFIRMED
                || !result.tradeId().equals(confirmed.referenciaResultado())) {
            throw new TokenMarketException(TokenMarketException.Reason.STALE_REQUEST,
                    "La compra P2P no tiene un resultado confirmable.");
        }
        return result;
    }

    private MarketP2PPurchaseResult replayOrPending(MarketP2PPurchaseCommand command,
            MarketIdempotencyResult result) {
        if (result.estado() == MarketIdempotencyStatus.CONFIRMED
                && result.referenciaResultado() != null) {
            return persistencia.replay(result.referenciaResultado(), command.usuarioId());
        }
        return new MarketP2PPurchaseResult(result.solicitudId(), null, command.offerId(),
                command.usuarioId(), null, result.estado());
    }

    private void validar(MarketP2PPurchaseCommand command) {
        try {
            if (command == null) {
                throw new IllegalArgumentException("El comando es obligatorio.");
            }
            command.validar();
        } catch (IllegalArgumentException exception) {
            throw new TokenMarketException(TokenMarketException.Reason.INVALID_INPUT,
                    "La solicitud de compra P2P no es valida.");
        }
    }
}
