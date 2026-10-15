package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.persistence.EstadoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.VersionCotizacionMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.mapper.VersionCotizacionMercadoMapper;
import com.devchesterunited.devchesterunitedv2.modelo.EstadoMercado;
import com.devchesterunited.devchesterunitedv2.service.dto.QuoteVersion;
import com.devchesterunited.devchesterunitedv2.service.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.interfaces.MarketQuoteValidationService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional(readOnly = true)
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
