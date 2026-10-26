package com.devchesterunited.devchesterunitedv2.service.impl.market;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.modelo.market.EstadoMercado;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.mapper.VersionCotizacionMercadoMapper;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.EstadoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.VersionCotizacionMercadoRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteVersion;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketQuoteValidationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** Valida una versión exacta sin calcular ni sustituir cotizaciones. */
@Service
public class MarketQuoteValidationServiceImpl implements MarketQuoteValidationService {
    private final EstadoMercadoRepository estados;
    private final JugadorRepository jugadores;
    private final VersionCotizacionMercadoRepository versiones;
    private final VersionCotizacionMercadoMapper mapper;
    private final Clock clock;

    public MarketQuoteValidationServiceImpl(EstadoMercadoRepository estados,
            JugadorRepository jugadores, VersionCotizacionMercadoRepository versiones,
            VersionCotizacionMercadoMapper mapper, Clock clock) {
        this.estados = estados;
        this.jugadores = jugadores;
        this.versiones = versiones;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    // findSingletonForRead usa PESSIMISTIC_READ (SELECT ... FOR SHARE).
    // La transaccion debe permanecer de solo lectura a nivel funcional, pero
    // no puede marcar la conexion PostgreSQL como read-only mientras adquiere
    // ese lock compartido.
    @Transactional
    public QuoteVersion validar(UUID playerId, UUID quoteId, long version) {
        validarEntrada(playerId, quoteId, version);
        EstadoMercado estado = estados.findSingletonForRead()
                .map(entity -> EstadoMercado.valueOf(entity.getStatus()))
                .orElseThrow(() -> mercadoNoOperativo());
        if (estado != EstadoMercado.READY) {
            throw mercadoNoOperativo();
        }
        if (jugadores.buscarActivoPorId(playerId).isEmpty()) {
            throw new TokenMarketException(TokenMarketException.Reason.INACTIVE_PLAYER,
                    "El jugador no esta activo.");
        }
        return versiones.findExactVigente(playerId, quoteId, version, clock.instant())
                .map(mapper::toDomain)
                .orElseThrow(() -> new TokenMarketException(
                        TokenMarketException.Reason.QUOTE_UNAVAILABLE,
                        "La cotizacion solicitada no esta vigente."));
    }

    private static void validarEntrada(UUID playerId, UUID quoteId, long version) {
        if (playerId == null || quoteId == null || version <= 0) {
            throw new TokenMarketException(TokenMarketException.Reason.INVALID_INPUT,
                    "El jugador y la version exacta de cotizacion son obligatorios.");
        }
    }

    private static TokenMarketException mercadoNoOperativo() {
        return new TokenMarketException(TokenMarketException.Reason.MARKET_NOT_READY,
                "El mercado no esta operativo.");
    }
}
