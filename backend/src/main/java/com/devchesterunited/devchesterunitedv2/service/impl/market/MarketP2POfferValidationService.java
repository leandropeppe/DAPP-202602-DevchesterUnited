package com.devchesterunited.devchesterunitedv2.service.impl.market;

import com.devchesterunited.devchesterunitedv2.modelo.market.EstadoMercado;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.CabeceraCotizacionMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.CabeceraCotizacionMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.EstadoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.VersionCotizacionMercadoRepository;
import com.devchesterunited.devchesterunitedv2.modelo.market.QuoteVersion;
import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** Adquiere y valida los locks comunes de una publicación P2P. */
@Service
public class MarketP2POfferValidationService {
    private final EstadoMercadoRepository estados;
    private final JugadorRepository jugadores;
    private final CabeceraCotizacionMercadoRepository cabeceras;
    private final VersionCotizacionMercadoRepository versiones;
    private final VersionCotizacionMercadoMapper mapper;
    private final Clock clock;

    public MarketP2POfferValidationService(EstadoMercadoRepository estados,
            JugadorRepository jugadores, CabeceraCotizacionMercadoRepository cabeceras,
            VersionCotizacionMercadoRepository versiones,
            VersionCotizacionMercadoMapper mapper, Clock clock) {
        this.estados = estados;
        this.jugadores = jugadores;
        this.cabeceras = cabeceras;
        this.versiones = versiones;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public QuoteVersion validar(UUID jugadorId) {
        EstadoMercado estado = estados.findSingletonForRead()
                .map(entity -> EstadoMercado.valueOf(entity.getStatus()))
                .orElseThrow(this::mercadoNoOperativo);
        if (estado != EstadoMercado.READY) {
            throw mercadoNoOperativo();
        }
        JugadorEntity jugador = jugadores.findByIdForUpdate(jugadorId)
                .orElseThrow(this::jugadorInactivo);
        if (!jugador.isActivo()) {
            throw jugadorInactivo();
        }
        CabeceraCotizacionMercadoEntity cabecera = cabeceras.findByJugadorIdForUpdate(jugadorId)
                .orElseThrow(this::cotizacionNoDisponible);
        return versiones.findExactVigente(jugadorId, cabecera.getQuoteId(),
                        cabecera.getVersionActual(), clock.instant())
                .map(mapper::toDomain)
                .orElseThrow(this::cotizacionNoDisponible);
    }

    private TokenMarketException jugadorInactivo() {
        return new TokenMarketException(TokenMarketException.Reason.INACTIVE_PLAYER,
                "El jugador no esta activo.");
    }

    private TokenMarketException cotizacionNoDisponible() {
        return new TokenMarketException(TokenMarketException.Reason.QUOTE_UNAVAILABLE,
                "La cotizacion oficial no esta vigente.");
    }

    private TokenMarketException mercadoNoOperativo() {
        return new TokenMarketException(TokenMarketException.Reason.MARKET_NOT_READY,
                "El mercado no esta operativo.");
    }
}
