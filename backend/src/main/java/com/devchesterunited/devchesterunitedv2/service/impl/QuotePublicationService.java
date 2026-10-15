package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.persistence.CabeceraCotizacionMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.VersionCotizacionMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.CabeceraCotizacionMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.VersionCotizacionMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.VersionCotizacionMercadoId;
import com.devchesterunited.devchesterunitedv2.persistence.mapper.VersionCotizacionMercadoMapper;
import com.devchesterunited.devchesterunitedv2.service.dto.QuotePublicationRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.QuoteValidityStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.QuoteVersion;
import com.devchesterunited.devchesterunitedv2.service.interfaces.QuotePublicationPort;
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
    @Transactional(readOnly = true)
    public Optional<QuoteVersion> currentQuote(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        return cabeceras.findById(playerId)
                .flatMap(this::versionForHeader)
                .map(mapper::toDomain);
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
        return mapper.toDomain(initial);
    }

    private Optional<VersionCotizacionMercadoEntity> versionForHeader(
            CabeceraCotizacionMercadoEntity header) {
        return versiones.findById(new VersionCotizacionMercadoId(
                header.getQuoteId(), header.getVersionActual()));
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
