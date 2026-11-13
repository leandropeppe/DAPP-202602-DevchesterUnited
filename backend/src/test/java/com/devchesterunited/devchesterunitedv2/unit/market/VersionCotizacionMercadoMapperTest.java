package com.devchesterunited.devchesterunitedv2.unit.market;

import com.devchesterunited.devchesterunitedv2.persistence.market.entity.VersionCotizacionMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.VersionCotizacionMercadoId;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.VigenciaCotizacionMercado;
import com.devchesterunited.devchesterunitedv2.service.impl.market.VersionCotizacionMercadoMapper;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteValidityStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteVersion;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VersionCotizacionMercadoMapperTest {
    private static final Instant PUBLICADA = Instant.parse("2026-10-14T12:00:00.123456Z");
    private static final Instant VALIDA_DESDE = Instant.parse("2026-10-14T12:00:01.123456Z");
    private static final Instant VALIDA_HASTA = Instant.parse("2026-10-15T12:00:01.123456Z");
    private static final Instant CREADA = Instant.parse("2026-10-14T12:00:02.123456Z");

    private final VersionCotizacionMercadoMapper mapper = new VersionCotizacionMercadoMapper();

    @Test
    void mapeaEntidadValidaConservandoIdentidadValoresYFechas() {
        UUID quoteId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        BigDecimal price = new BigDecimal("123.40");
        VersionCotizacionMercadoEntity entity = entity(
                quoteId, 7L, playerId, price, PUBLICADA, VALIDA_DESDE,
                VALIDA_HASTA, "VALID", "valuation-v2");

        QuoteVersion result = mapper.toDomain(entity);

        assertThat(result.quoteId()).isEqualTo(quoteId);
        assertThat(result.version()).isEqualTo(7L);
        assertThat(result.playerId()).isEqualTo(playerId);
        assertThat(result.pricePerToken()).isEqualTo(price);
        assertThat(result.pricePerToken().scale()).isEqualTo(2);
        assertThat(result.publishedAt()).isEqualTo(PUBLICADA);
        assertThat(result.validFrom()).isEqualTo(VALIDA_DESDE);
        assertThat(result.validUntil()).isEqualTo(VALIDA_HASTA);
        assertThat(result.validityStatus()).isEqualTo(QuoteValidityStatus.VALID);
        assertThat(result.strategyReference()).isEqualTo("valuation-v2");
    }

    @Test
    void conservaValidoHastaNuloYReferenciaDeEstrategiaNula() {
        VersionCotizacionMercadoEntity entity = entity(
                UUID.randomUUID(), 1L, UUID.randomUUID(), new BigDecimal("1.00"),
                PUBLICADA, PUBLICADA, null, "VALID", null);

        QuoteVersion result = mapper.toDomain(entity);

        assertThat(result.validUntil()).isNull();
        assertThat(result.strategyReference()).isNull();
    }

    @Test
    void aceptaTodosLosEstadosPersistidosDelContrato() {
        for (QuoteValidityStatus status : QuoteValidityStatus.values()) {
            VersionCotizacionMercadoEntity entity = entity(
                    UUID.randomUUID(), 1L, UUID.randomUUID(), new BigDecimal("1.00"),
                    PUBLICADA, PUBLICADA, null, status.name(), null);

            assertThat(mapper.toDomain(entity).validityStatus()).isEqualTo(status);
        }
    }

    @Test
    void rechazaEstadoPersistidoDesconocido() {
        VersionCotizacionMercadoEntity entity = entity(
                UUID.randomUUID(), 1L, UUID.randomUUID(), new BigDecimal("1.00"),
                PUBLICADA, PUBLICADA, null, "UNKNOWN", null);

        assertThatThrownBy(() -> mapper.toDomain(entity))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaEntidadNula() {
        assertThatThrownBy(() -> mapper.toDomain(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("entidad es obligatoria");
    }

    @Test
    void noModificaLaEntidadDeOrigen() {
        UUID quoteId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        BigDecimal price = new BigDecimal("1.00");
        VersionCotizacionMercadoEntity entity = entity(
                quoteId, 3L, playerId, price, PUBLICADA, PUBLICADA,
                null, "EXPIRED", "strategy");

        mapper.toDomain(entity);

        assertThat(entity.getId().getQuoteId()).isEqualTo(quoteId);
        assertThat(entity.getId().getVersion()).isEqualTo(3L);
        assertThat(entity.getJugadorId()).isEqualTo(playerId);
        assertThat(entity.getPrecioPorToken()).isEqualTo(price);
        assertThat(entity.getPublicadoEn()).isEqualTo(PUBLICADA);
        assertThat(entity.getValidoDesde()).isEqualTo(PUBLICADA);
        assertThat(entity.getValidoHasta()).isNull();
        assertThat(entity.getEstadoVigencia()).isEqualTo("EXPIRED");
        assertThat(entity.getReferenciaEstrategia()).isEqualTo("strategy");
    }

    private VersionCotizacionMercadoEntity entity(
            UUID quoteId,
            Long version,
            UUID playerId,
            BigDecimal price,
            Instant publishedAt,
            Instant validFrom,
            Instant validUntil,
            String status,
            String strategyReference) {
        return new VersionCotizacionMercadoEntity(
                new VersionCotizacionMercadoId(quoteId, version),
                playerId,
                price,
                new VigenciaCotizacionMercado(publishedAt, validFrom, validUntil),
                status,
                strategyReference,
                CREADA);
    }
}
