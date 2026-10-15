package com.devchesterunited.devchesterunitedv2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.persistence.CabeceraCotizacionMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.VersionCotizacionMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.CabeceraCotizacionMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.VersionCotizacionMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.VersionCotizacionMercadoId;
import com.devchesterunited.devchesterunitedv2.persistence.mapper.VersionCotizacionMercadoMapper;
import com.devchesterunited.devchesterunitedv2.service.dto.QuotePublicationRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.QuoteVersion;
import com.devchesterunited.devchesterunitedv2.service.impl.QuotePublicationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuotePublicationServiceTest {
    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final UUID QUOTE_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    @Mock JugadorRepository jugadores;
    @Mock VersionCotizacionMercadoRepository versiones;
    @Mock CabeceraCotizacionMercadoRepository cabeceras;

    private QuotePublicationService service;

    @BeforeEach
    void setUp() {
        service = new QuotePublicationService(jugadores, versiones, cabeceras,
                new VersionCotizacionMercadoMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void publicaLaInicialConUnPesoYVigenciaNoExpirable() {
        when(jugadores.findByIdForUpdate(PLAYER_ID)).thenReturn(Optional.of(player()));
        when(cabeceras.findByJugadorIdForUpdate(PLAYER_ID)).thenReturn(Optional.empty());

        QuoteVersion result = service.publishInitialQuote(PLAYER_ID,
                new BigDecimal("1.00"), true);

        assertThat(result.version()).isEqualTo(1L);
        assertThat(result.playerId()).isEqualTo(PLAYER_ID);
        assertThat(result.pricePerToken()).isEqualByComparingTo("1.00");
        assertThat(result.validUntil()).isNull();
        verify(versiones).saveAndFlush(any(VersionCotizacionMercadoEntity.class));
        verify(cabeceras).saveAndFlush(any(CabeceraCotizacionMercadoEntity.class));
    }

    @Test
    void reintentoInicialDevuelveLaVersionUnoYSinCrearOtraVersion() {
        when(jugadores.findByIdForUpdate(PLAYER_ID)).thenReturn(Optional.of(player()));
        CabeceraCotizacionMercadoEntity header =
                new CabeceraCotizacionMercadoEntity(PLAYER_ID, QUOTE_ID, 2L, NOW);
        VersionCotizacionMercadoEntity initial = version(QUOTE_ID, 1L,
                new BigDecimal("1.00"), null);
        when(cabeceras.findByJugadorIdForUpdate(PLAYER_ID)).thenReturn(Optional.of(header));
        when(versiones.findById(new VersionCotizacionMercadoId(QUOTE_ID, 1L)))
                .thenReturn(Optional.of(initial));

        QuoteVersion result = service.publishInitialQuote(PLAYER_ID,
                new BigDecimal("1.00"), true);

        assertThat(result.version()).isEqualTo(1L);
        verify(versiones, never()).saveAndFlush(any());
        verify(cabeceras, never()).saveAndFlush(any());
    }

    @Test
    void publicacionFuturaMantieneElMismoQuoteIdYAvanzaVersion() {
        when(jugadores.findByIdForUpdate(PLAYER_ID)).thenReturn(Optional.of(player()));
        CabeceraCotizacionMercadoEntity header =
                new CabeceraCotizacionMercadoEntity(PLAYER_ID, QUOTE_ID, 1L, NOW);
        when(cabeceras.findByJugadorIdForUpdate(PLAYER_ID)).thenReturn(Optional.of(header));
        QuotePublicationRequest request = new QuotePublicationRequest(
                PLAYER_ID, new BigDecimal("1.25"), NOW.plusSeconds(60),
                NOW.plusSeconds(60), null, "valuation-v1");

        QuoteVersion result = service.publish(request);

        assertThat(result.quoteId()).isEqualTo(QUOTE_ID);
        assertThat(result.version()).isEqualTo(2L);
        assertThat(header.getVersionActual()).isEqualTo(2L);
        assertThat(header.getQuoteId()).isEqualTo(QUOTE_ID);
        verify(versiones).saveAndFlush(any(VersionCotizacionMercadoEntity.class));
        verify(cabeceras).saveAndFlush(header);
    }

    @Test
    void rechazaParametrosInicialesQueNoCumplenElContrato() {
        assertThatThrownBy(() -> service.publishInitialQuote(PLAYER_ID,
                new BigDecimal("1.01"), true)).isInstanceOf(IllegalArgumentException.class);
        verify(jugadores, never()).findByIdForUpdate(any());
    }

    private static JugadorEntity player() {
        return new JugadorEntity(PLAYER_ID, "T040 Player", UUID.randomUUID(),
                "Forward", 10, "Argentina", true);
    }

    private static VersionCotizacionMercadoEntity version(UUID quoteId, long number,
            BigDecimal price, Instant validUntil) {
        return new VersionCotizacionMercadoEntity(
                new VersionCotizacionMercadoId(quoteId, number), PLAYER_ID, price,
                NOW, NOW, validUntil, "VALID", null, NOW);
    }
}
