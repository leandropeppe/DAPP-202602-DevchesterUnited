package com.devchesterunited.devchesterunitedv2.service.impl.market;

import com.devchesterunited.devchesterunitedv2.persistence.jugador.projection.JugadorPortfolioProjection;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.TenenciaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.mapper.VersionCotizacionMercadoMapper;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.PnLRealizadoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.TenenciaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.VersionCotizacionMercadoRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.market.*;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketPortfolioService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.QuotePublicationPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** Agrega las posiciones abiertas y su valoración del usuario autenticado. */
@Service
public class MarketPortfolioServiceImpl implements MarketPortfolioService {
    private static final int INTERNAL_SCALE = 8;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(INTERNAL_SCALE);

    private final TenenciaMercadoRepository tenencias;
    private final PnLRealizadoMercadoRepository pnlRealizado;
    private final QuotePublicationPort cotizaciones;
    private final VersionCotizacionMercadoRepository versiones;
    private final VersionCotizacionMercadoMapper mapper;
    private final Clock clock;
    private final JugadorRepository jugadores;

    @Autowired
    public MarketPortfolioServiceImpl(TenenciaMercadoRepository tenencias,
            PnLRealizadoMercadoRepository pnlRealizado,
            QuotePublicationPort cotizaciones,
            VersionCotizacionMercadoRepository versiones,
            VersionCotizacionMercadoMapper mapper,
            Clock clock,
            JugadorRepository jugadores) {
        this.tenencias = Objects.requireNonNull(tenencias);
        this.pnlRealizado = Objects.requireNonNull(pnlRealizado);
        this.cotizaciones = Objects.requireNonNull(cotizaciones);
        this.versiones = Objects.requireNonNull(versiones);
        this.mapper = Objects.requireNonNull(mapper);
        this.clock = Objects.requireNonNull(clock);
        this.jugadores = Objects.requireNonNull(jugadores);
    }

    /** Constructor de compatibilidad para consumidores de servicio existentes. */
    public MarketPortfolioServiceImpl(TenenciaMercadoRepository tenencias,
            PnLRealizadoMercadoRepository pnlRealizado,
            QuotePublicationPort cotizaciones,
            VersionCotizacionMercadoRepository versiones,
            VersionCotizacionMercadoMapper mapper,
            Clock clock) {
        this.tenencias = Objects.requireNonNull(tenencias);
        this.pnlRealizado = Objects.requireNonNull(pnlRealizado);
        this.cotizaciones = Objects.requireNonNull(cotizaciones);
        this.versiones = Objects.requireNonNull(versiones);
        this.mapper = Objects.requireNonNull(mapper);
        this.clock = Objects.requireNonNull(clock);
        this.jugadores = null;
    }

    @Override
    @Transactional(readOnly = true)
    public MarketPortfolioResult consultar(UUID usuarioId) {
        Objects.requireNonNull(usuarioId, "El usuario del portfolio es obligatorio.");
        Instant momento = clock.instant();
        List<MarketPortfolioPosition> posiciones = new ArrayList<>();
        boolean todasValuadas = true;
        BigDecimal pnlNoRealizadoTotal = ZERO;

        for (TenenciaMercadoEntity tenencia : tenencias.findAllByUsuarioId(usuarioId)) {
            if (tenencia.getQuantity() == null || tenencia.getQuantity() <= 0) {
                continue;
            }

            PositionValuation valuacion = valorar(tenencia, momento);
            posiciones.add(valuacion.posicion());
            if (valuacion.pnlNoRealizado().isPresent()) {
                pnlNoRealizadoTotal = pnlNoRealizadoTotal.add(valuacion.pnlNoRealizado().get())
                        .setScale(INTERNAL_SCALE, RoundingMode.HALF_EVEN);
            } else {
                todasValuadas = false;
            }
        }

        Map<UUID, JugadorPortfolioProjection> descripciones = cargarDescripciones(posiciones);
        posiciones = posiciones.stream()
                .map(position -> enriquecer(position, descripciones.get(position.jugadorId())))
                .toList();

        long tokensTotales = posiciones.stream()
                .mapToLong(MarketPortfolioPosition::cantidad)
                .sum();
        BigDecimal costoPendienteTotal = posiciones.stream()
                .map(MarketPortfolioPosition::costoTotalPendiente)
                .reduce(ZERO, (total, costo) -> total.add(costo)
                        .setScale(INTERNAL_SCALE, RoundingMode.HALF_EVEN));
        Optional<BigDecimal> valorActualTotal = todasValuadas
                ? Optional.of(posiciones.stream()
                        .flatMap(position -> position.valorActual().stream())
                        .reduce(ZERO, (total, valor) -> total.add(valor)
                                .setScale(INTERNAL_SCALE, RoundingMode.HALF_EVEN)))
                : Optional.empty();

        BigDecimal realizado = pnlRealizado.findById(usuarioId)
                .map(entity -> entity.getPnlRealizado().setScale(INTERNAL_SCALE,
                        RoundingMode.HALF_EVEN))
                .orElse(ZERO);
        Optional<BigDecimal> noRealizado = todasValuadas
                ? Optional.of(pnlNoRealizadoTotal)
                : Optional.empty();
        Optional<BigDecimal> combinado = todasValuadas
                ? Optional.of(realizado.add(pnlNoRealizadoTotal)
                        .setScale(INTERNAL_SCALE, RoundingMode.HALF_EVEN))
                : Optional.empty();

        return new MarketPortfolioResult(posiciones, realizado, noRealizado, combinado,
                todasValuadas, posiciones.size(), tokensTotales, costoPendienteTotal,
                valorActualTotal);
    }

    private Map<UUID, JugadorPortfolioProjection> cargarDescripciones(
            Collection<MarketPortfolioPosition> posiciones) {
        List<UUID> jugadorIds = posiciones.stream().map(MarketPortfolioPosition::jugadorId)
                .distinct().toList();
        if (jugadorIds.isEmpty()) {
            return Map.of();
        }
        if (jugadores == null) {
            return Map.of();
        }
        Map<UUID, JugadorPortfolioProjection> result = new HashMap<>();
        List<JugadorPortfolioProjection> descripciones = jugadores.findPortfolioDescriptions(jugadorIds);
        if (descripciones == null) {
            return result;
        }
        for (JugadorPortfolioProjection descripcion : descripciones) {
            result.put(descripcion.getPlayerId(), descripcion);
        }
        return result;
    }

    private MarketPortfolioPosition enriquecer(MarketPortfolioPosition position,
            JugadorPortfolioProjection descripcion) {
        if (descripcion == null) {
            return position;
        }
        return position.conDescripcion(descripcion.getPlayerName(), descripcion.getTeam(),
                descripcion.getLeague());
    }

    private PositionValuation valorar(TenenciaMercadoEntity tenencia, Instant momento) {
        UUID jugadorId = tenencia.getId().getJugadorId();
        long cantidad = tenencia.getQuantity();
        BigDecimal costoTotal = tenencia.getCostoAdquisicion()
                .setScale(INTERNAL_SCALE, RoundingMode.HALF_EVEN);
        BigDecimal costoPromedio = costoTotal.divide(BigDecimal.valueOf(cantidad),
                INTERNAL_SCALE, RoundingMode.HALF_EVEN);

        Optional<QuoteVersion> actual = cotizaciones.currentQuote(jugadorId)
                .filter(quote -> quote.isValidAt(momento))
                .filter(MarketPortfolioServiceImpl::isOperationalValuation);
        if (actual.isPresent()) {
            return currentPosition(jugadorId, cantidad, costoTotal, costoPromedio,
                    actual.get());
        }

        Optional<QuoteVersion> historica = versiones.findAllByJugadorIdOrderByPublicadoEnDesc(
                        jugadorId)
                .stream()
                .findFirst()
                .map(mapper::toDomain);
        MarketPortfolioQuote quote = historica.map(this::toPortfolioQuote).orElse(null);
        return new PositionValuation(
                new MarketPortfolioPosition(
                        jugadorId,
                        cantidad,
                        costoTotal,
                        costoPromedio,
                        quote == null ? MarketQuoteAvailability.ABSENT
                                : MarketQuoteAvailability.HISTORICAL,
                        Optional.ofNullable(quote),
                        Optional.empty(),
                        Optional.empty()),
                Optional.empty());
    }

    private static boolean isOperationalValuation(QuoteVersion quote) {
        return quote.strategyReference() != null && !quote.strategyReference().isBlank()
                || quote.pricePerToken().compareTo(BigDecimal.ONE) != 0;
    }

    private PositionValuation currentPosition(UUID jugadorId, long cantidad,
            BigDecimal costoTotal, BigDecimal costoPromedio, QuoteVersion quote) {
        BigDecimal valorActual = quote.pricePerToken()
                .multiply(BigDecimal.valueOf(cantidad))
                .setScale(INTERNAL_SCALE, RoundingMode.HALF_EVEN);
        BigDecimal pnl = valorActual.subtract(costoTotal)
                .setScale(INTERNAL_SCALE, RoundingMode.HALF_EVEN);
        return new PositionValuation(
                new MarketPortfolioPosition(
                        jugadorId,
                        cantidad,
                        costoTotal,
                        costoPromedio,
                        MarketQuoteAvailability.CURRENT,
                        Optional.of(toPortfolioQuote(quote)),
                        Optional.of(valorActual),
                        Optional.of(pnl)),
                Optional.of(pnl));
    }

    private MarketPortfolioQuote toPortfolioQuote(QuoteVersion quote) {
        return new MarketPortfolioQuote(
                quote.quoteId(),
                quote.version(),
                quote.pricePerToken(),
                quote.publishedAt(),
                quote.validFrom(),
                quote.validUntil());
    }

    private record PositionValuation(
            MarketPortfolioPosition posicion,
            Optional<BigDecimal> pnlNoRealizado) { }
}
