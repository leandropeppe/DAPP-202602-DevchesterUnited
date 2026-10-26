package com.devchesterunited.devchesterunitedv2.service.impl.market;

import com.devchesterunited.devchesterunitedv2.modelo.market.EstadoMercado;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.EstadoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.MarketAvailabilityRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.projection.MarketPlayerAvailabilityProjection;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketAvailabilityResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPlayerAvailability;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketQuoteAvailability;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketAvailabilityService;
import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.UUID;

@Service
public class MarketAvailabilityServiceImpl implements MarketAvailabilityService {
    private static final int DEFAULT_PAGE = 1;
    private static final int MAX_LIMIT = 100;

    private final EstadoMercadoRepository estados;
    private final MarketAvailabilityRepository mercado;
    private final Clock clock;

    public MarketAvailabilityServiceImpl(EstadoMercadoRepository estados,
            MarketAvailabilityRepository mercado, Clock clock) {
        this.estados = estados;
        this.mercado = mercado;
        this.clock = clock;
    }

    @Override
    // findSingletonForRead usa PESSIMISTIC_READ y Hibernate lo traduce a
    // SELECT ... FOR SHARE. PostgreSQL no permite ese lock dentro de una
    // transaccion marcada como read-only a nivel de conexion.
    @Transactional
    public MarketAvailabilityResult consultarJugadores(int pagina, int limite) {
        validarPaginacion(pagina, limite);
        var estado = estados.findSingletonForRead().orElseThrow(() ->
                new IllegalStateException("No existe la fila singleton del estado del mercado."));
        EstadoMercado estadoMercado = EstadoMercado.valueOf(estado.getStatus());
        Instant momento = clock.instant();
        Page<MarketPlayerAvailabilityProjection> page = mercado.findAllForMarket(
                PageRequest.of(pagina - 1, limite));

        boolean operativa = estadoMercado == EstadoMercado.READY;
        return new MarketAvailabilityResult(
                estadoMercado,
                estado.getEpoch(),
                operativa,
                pagina,
                limite,
                page.getTotalElements(),
                page.getTotalPages(),
                page.getContent().stream()
                        .map(proyeccion -> mapear(proyeccion, operativa, momento))
                        .toList());
    }

    @Override
    @Transactional
    public Optional<MarketPlayerAvailability> consultarJugador(UUID jugadorId) {
        var estado = estados.findSingletonForRead().orElseThrow(() ->
                new IllegalStateException("No existe la fila singleton del estado del mercado."));
        boolean operativa = EstadoMercado.valueOf(estado.getStatus()) == EstadoMercado.READY;
        if (!operativa) {
            throw new TokenMarketException(TokenMarketException.Reason.MARKET_NOT_READY,
                    "El mercado no esta operativo.");
        }
        Instant momento = clock.instant();
        return mercado.findForMarket(jugadorId)
                .map(proyeccion -> mapear(proyeccion, operativa, momento));
    }

    private static MarketPlayerAvailability mapear(
            MarketPlayerAvailabilityProjection projection,
            boolean mercadoOperativo,
            Instant momento) {
        boolean quoteCurrent = esVigente(projection.getCurrentValidityStatus(),
                projection.getCurrentValidFrom(), projection.getCurrentValidUntil(), momento);
        boolean currentQuotePresent = projection.getCurrentQuoteId() != null;
        boolean realCurrentQuote = currentQuotePresent
                && (projection.getCurrentStrategyReference() != null
                        && !projection.getCurrentStrategyReference().isBlank()
                        || projection.getCurrentPricePerToken() == null
                        || projection.getCurrentPricePerToken().compareTo(java.math.BigDecimal.ONE) != 0);
        boolean quotePresent = currentQuotePresent
                || projection.getLatestQuoteId() != null;
        boolean quoteHistorical = quotePresent && !quoteCurrent;

        // Si existe cabecera, esa versión es la única referencia actual, aunque
        // esté invalidada o vencida. Nunca se promociona una versión histórica.
        boolean useCurrentQuote = currentQuotePresent;
        UUIDAndVersion quote = useCurrentQuote
                ? new UUIDAndVersion(projection.getCurrentQuoteId(), projection.getCurrentQuoteVersion())
                : new UUIDAndVersion(projection.getLatestQuoteId(), projection.getLatestQuoteVersion());
        boolean operable = mercadoOperativo
                && projection.getPlayerActive()
                && projection.getTeamActive()
                && projection.getIssuedQuantity() != null
                && projection.getIssuedQuantity() > 0
                && projection.getInventoryQuantity() != null
                && projection.getInventoryQuantity() > 0
                && quoteCurrent
                && realCurrentQuote;

        return new MarketPlayerAvailability(
                projection.getPlayerId(),
                projection.getPlayerName(),
                projection.getLeague(),
                projection.getTeamName(),
                projection.getPlayerActive(),
                projection.getTeamActive(),
                valueOrZero(projection.getIssuedQuantity()),
                valueOrZero(projection.getInventoryQuantity()),
                quote.id(),
                quote.version(),
                useCurrentQuote ? projection.getCurrentPricePerToken() : projection.getLatestPricePerToken(),
                useCurrentQuote ? projection.getCurrentValidityStatus() : projection.getLatestValidityStatus(),
                useCurrentQuote ? projection.getCurrentValidFrom() : projection.getLatestValidFrom(),
                useCurrentQuote ? projection.getCurrentValidUntil() : projection.getLatestValidUntil(),
                !quotePresent ? MarketQuoteAvailability.ABSENT
                        : quoteHistorical ? MarketQuoteAvailability.HISTORICAL
                        : MarketQuoteAvailability.CURRENT,
                operable);
    }

    private static boolean esVigente(String status, Instant desde, Instant hasta, Instant momento) {
        return "VALID".equals(status)
                && desde != null
                && !momento.isBefore(desde)
                && (hasta == null || momento.isBefore(hasta));
    }

    private static long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private static void validarPaginacion(int pagina, int limite) {
        if (pagina < DEFAULT_PAGE) {
            throw new IllegalArgumentException("La página debe ser positiva.");
        }
        if (limite < 1 || limite > MAX_LIMIT) {
            throw new IllegalArgumentException("El límite debe estar entre 1 y 100.");
        }
    }

    private record UUIDAndVersion(java.util.UUID id, Long version) { }
}
