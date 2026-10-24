package com.devchesterunited.devchesterunitedv2.service.impl.market;

import com.devchesterunited.devchesterunitedv2.persistence.market.repository.CabeceraCotizacionMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.VersionCotizacionMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.CabeceraCotizacionMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.VersionCotizacionMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.VersionCotizacionMercadoId;
import com.devchesterunited.devchesterunitedv2.persistence.market.mapper.VersionCotizacionMercadoMapper;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuotePublicationRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteInvalidationRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteInvalidationResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteValidityStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteVersion;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.QuotePublicationPort;
import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Única implementación persistente del límite de publicación de cotizaciones. */
@Service
public class QuotePublicationService implements QuotePublicationPort {
    private static final BigDecimal INITIAL_PRICE = new BigDecimal("1.00");

    private final JugadorRepository jugadores;
    private final VersionCotizacionMercadoRepository versiones;
    private final CabeceraCotizacionMercadoRepository cabeceras;
    private final VersionCotizacionMercadoMapper mapper;
    private final Clock clock;

    public QuotePublicationService(JugadorRepository jugadores,
            VersionCotizacionMercadoRepository versiones,
            CabeceraCotizacionMercadoRepository cabeceras,
            VersionCotizacionMercadoMapper mapper, Clock clock) {
        this.jugadores = jugadores;
        this.versiones = versiones;
        this.cabeceras = cabeceras;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public QuoteVersion publishInitialQuote(UUID playerId, BigDecimal pricePerToken,
            boolean nonExpiring) {
        QuotePublicationPort.validateInitialQuotePublication(
                playerId, pricePerToken, nonExpiring);
        lockPlayer(playerId);
        Optional<CabeceraCotizacionMercadoEntity> current =
                cabeceras.findByJugadorIdForUpdate(playerId);
        if (current.isPresent()) {
            return initialVersion(current.get());
        }

        return publishFirstVersion(QuotePublicationRequest.initial(playerId, clock.instant()));
    }

    @Override
    @Transactional
    public QuoteVersion publish(QuotePublicationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud de cotización es obligatoria.");
        }
        lockPlayer(request.playerId());
        Optional<CabeceraCotizacionMercadoEntity> current =
                cabeceras.findByJugadorIdForUpdate(request.playerId());
        if (current.isEmpty()) {
            return publishFirstVersion(request);
        }

        CabeceraCotizacionMercadoEntity header = current.get();
        long nextVersion = header.getVersionActual() + 1;
        VersionCotizacionMercadoId id = new VersionCotizacionMercadoId(
                header.getQuoteId(), nextVersion);
        VersionCotizacionMercadoEntity version = versionEntity(id, request);
        versiones.saveAndFlush(version);
        header.avanzarA(id.getQuoteId(), id.getVersion(), clock.instant());
        cabeceras.saveAndFlush(header);
        return mapper.toDomain(version);
    }

    @Override
    @Transactional
    public QuoteInvalidationResult invalidate(QuoteInvalidationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud de invalidacion es obligatoria.");
        }
        // Publication and invalidation use the same lock order: player, then header.
        lockPlayer(request.playerId());
        CabeceraCotizacionMercadoEntity header = cabeceras
                .findByJugadorIdForUpdate(request.playerId())
                .orElseThrow(() -> quoteUnavailable("La cabecera de cotizacion no existe."));

        if (request.quoteId().equals(header.getQuoteId())
                && request.version() == header.getVersionActual()
                && "INVALIDATED".equals(header.getEstadoVigencia())) {
            return invalidationResult(header);
        }
        if (!request.quoteId().equals(header.getQuoteId())
                || request.version() != header.getVersionActual()
                || !"VALID".equals(header.getEstadoVigencia())) {
            throw quoteUnavailable("La cotizacion ya no es la referencia vigente.");
        }

        VersionCotizacionMercadoEntity entity = versiones.findExact(
                        request.playerId(), request.quoteId(), request.version())
                .orElseThrow(() -> quoteUnavailable("La version de cotizacion no existe."));
        QuoteVersion current = mapper.toDomain(entity);
        Instant now = canonicalTimestamp(clock.instant());
        if (!current.isValidAt(now)) {
            throw quoteUnavailable("La cotizacion ya no esta vigente.");
        }

        header.invalidar(now, request.reason());
        cabeceras.saveAndFlush(header);
        return invalidationResult(header);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<QuoteVersion> currentQuote(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        return cabeceras.findById(playerId)
                .flatMap(header -> versionForHeader(header)
                        .map(version -> toEffectiveDomain(version, header.getEstadoVigencia())));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<QuoteVersion> find(UUID quoteId, long version) {
        if (quoteId == null || version <= 0) {
            return Optional.empty();
        }
        return versiones.findById(new VersionCotizacionMercadoId(quoteId, version))
                .map(mapper::toDomain);
    }

    private QuoteVersion publishFirstVersion(QuotePublicationRequest request) {
        UUID quoteId = UUID.randomUUID();
        VersionCotizacionMercadoId id = new VersionCotizacionMercadoId(quoteId, 1L);
        VersionCotizacionMercadoEntity version = versionEntity(id, request);
        versiones.saveAndFlush(version);
        cabeceras.saveAndFlush(new CabeceraCotizacionMercadoEntity(
                request.playerId(), quoteId, 1L, clock.instant()));
        return mapper.toDomain(version);
    }

    private VersionCotizacionMercadoEntity versionEntity(VersionCotizacionMercadoId id,
            QuotePublicationRequest request) {
        return new VersionCotizacionMercadoEntity(
                id,
                request.playerId(),
                request.pricePerToken(),
                canonicalTimestamp(request.publishedAt()),
                canonicalTimestamp(request.validFrom()),
                request.validUntil() == null ? null : canonicalTimestamp(request.validUntil()),
                QuoteValidityStatus.VALID.name(),
                request.strategyReference(),
                canonicalTimestamp(clock.instant()));
    }

    private QuoteVersion initialVersion(CabeceraCotizacionMercadoEntity header) {
        VersionCotizacionMercadoId initialId = new VersionCotizacionMercadoId(
                header.getQuoteId(), 1L);
        VersionCotizacionMercadoEntity initial = versiones.findById(initialId)
                .orElseThrow(() -> new IllegalStateException(
                        "La cabecera no referencia una cotización inicial válida."));
        if (initial.getPrecioPorToken().compareTo(INITIAL_PRICE) != 0
                || initial.getValidoHasta() != null
                || !QuoteValidityStatus.VALID.name().equals(initial.getEstadoVigencia())) {
            throw new IllegalStateException(
                    "La cotización inicial persistida es incompatible con el contrato.");
        }
        return toEffectiveDomain(initial, header.getEstadoVigencia());
    }

    private Optional<VersionCotizacionMercadoEntity> versionForHeader(
            CabeceraCotizacionMercadoEntity header) {
        return versiones.findById(new VersionCotizacionMercadoId(
                header.getQuoteId(), header.getVersionActual()));
    }

    private QuoteInvalidationResult invalidationResult(CabeceraCotizacionMercadoEntity header) {
        return new QuoteInvalidationResult(
                header.getJugadorId(), header.getQuoteId(), header.getVersionActual(),
                QuoteValidityStatus.INVALIDATED, header.getInvalidatedAt(),
                header.getInvalidationReason());
    }

    private QuoteVersion toEffectiveDomain(VersionCotizacionMercadoEntity entity,
            String headerStatus) {
        QuoteVersion quote = mapper.toDomain(entity);
        if (!"INVALIDATED".equals(headerStatus)
                || quote.validityStatus() == QuoteValidityStatus.INVALIDATED) {
            return quote;
        }
        return new QuoteVersion(quote.quoteId(), quote.version(), quote.playerId(),
                quote.pricePerToken(), quote.publishedAt(), quote.validFrom(),
                quote.validUntil(), QuoteValidityStatus.INVALIDATED,
                quote.strategyReference());
    }

    private static TokenMarketException quoteUnavailable(String message) {
        return new TokenMarketException(TokenMarketException.Reason.QUOTE_UNAVAILABLE, message);
    }

    private void lockPlayer(UUID playerId) {
        jugadores.findByIdForUpdate(playerId).orElseThrow(() ->
                new IllegalArgumentException("No existe el jugador de la cotización."));
    }

    /** PostgreSQL TIMESTAMPTZ conserva precisión de microsegundos. */
    private static Instant canonicalTimestamp(Instant timestamp) {
        return timestamp.truncatedTo(ChronoUnit.MICROS);
    }
}
