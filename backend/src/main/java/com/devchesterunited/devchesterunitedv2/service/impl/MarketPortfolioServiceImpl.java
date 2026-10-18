package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.persistence.PnLRealizadoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.TenenciaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.VersionCotizacionMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.TenenciaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.mapper.VersionCotizacionMercadoMapper;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketPortfolioPosition;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketPortfolioQuote;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketPortfolioResult;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketQuoteAvailability;
import com.devchesterunited.devchesterunitedv2.service.dto.QuoteVersion;
import com.devchesterunited.devchesterunitedv2.service.interfaces.MarketPortfolioService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.QuotePublicationPort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                todasValuadas);
    }

    private PositionValuation valorar(TenenciaMercadoEntity tenencia, Instant momento) {
        UUID jugadorId = tenencia.getId().getJugadorId();
        long cantidad = tenencia.getQuantity();
        BigDecimal costoTotal = tenencia.getCostoAdquisicion()
                .setScale(INTERNAL_SCALE, RoundingMode.HALF_EVEN);
        BigDecimal costoPromedio = costoTotal.divide(BigDecimal.valueOf(cantidad),
                INTERNAL_SCALE, RoundingMode.HALF_EVEN);

        Optional<QuoteVersion> actual = cotizaciones.currentQuote(jugadorId)
                .filter(quote -> quote.isValidAt(momento));
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
