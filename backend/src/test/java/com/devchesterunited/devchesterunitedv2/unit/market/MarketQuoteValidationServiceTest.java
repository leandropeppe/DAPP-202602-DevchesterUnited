package com.devchesterunited.devchesterunitedv2.unit.market;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.modelo.market.EstadoMercado;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.projection.JugadorCatalogoProjection;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.EstadoMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.VersionCotizacionMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.VersionCotizacionMercadoId;
import com.devchesterunited.devchesterunitedv2.persistence.market.mapper.VersionCotizacionMercadoMapper;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.EstadoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.VersionCotizacionMercadoRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteValidityStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteVersion;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketQuoteValidationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class MarketQuoteValidationServiceTest {
    private static final UUID PLAYER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID QUOTE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    @Mock EstadoMercadoRepository estados;
    @Mock JugadorRepository jugadores;
    @Mock VersionCotizacionMercadoRepository versiones;
    @Mock JugadorCatalogoProjection jugadorActivo;

    private MarketQuoteValidationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MarketQuoteValidationServiceImpl(estados, jugadores, versiones,
                new VersionCotizacionMercadoMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void readyJugadorActivoYVersionExactaDevuelveLaCotizacion() {
        givenReadyAndActivePlayer();
        VersionCotizacionMercadoEntity entity = quoteEntity(QuoteValidityStatus.VALID, null);
        when(versiones.findExactVigente(PLAYER_ID, QUOTE_ID, 3L, NOW))
                .thenReturn(Optional.of(entity));

        QuoteVersion result = service.validar(PLAYER_ID, QUOTE_ID, 3L);

        assertThat(result.quoteId()).isEqualTo(QUOTE_ID);
        assertThat(result.version()).isEqualTo(3L);
        assertThat(result.pricePerToken()).isEqualByComparingTo("1.25");
        verify(versiones).findExactVigente(PLAYER_ID, QUOTE_ID, 3L, NOW);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoMercado.class, names = {"UNINITIALIZED", "INITIALIZING", "FAILED"})
    void estadosNoReadyRechazanAntesDeConsultarJugador(EstadoMercado estado) {
        when(estados.findSingletonForRead()).thenReturn(Optional.of(state(estado)));

        assertThatThrownBy(() -> service.validar(PLAYER_ID, QUOTE_ID, 1L))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(TokenMarketException.Reason.MARKET_NOT_READY));

        verify(jugadores, never()).buscarActivoPorId(PLAYER_ID);
        verify(versiones, never()).findExactVigente(PLAYER_ID, QUOTE_ID, 1L, NOW);
    }

    @Test
    void mercadoSinEstadoSingletonSeConsideraNoOperativo() {
        when(estados.findSingletonForRead()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validar(PLAYER_ID, QUOTE_ID, 1L))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(TokenMarketException.Reason.MARKET_NOT_READY));
    }

    @Test
    void jugadorInactivoRechazaLaCotizacionAunqueLaVersionExista() {
        givenReadyState();
        when(jugadores.buscarActivoPorId(PLAYER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validar(PLAYER_ID, QUOTE_ID, 1L))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(TokenMarketException.Reason.INACTIVE_PLAYER));

        verify(versiones, never()).findExactVigente(PLAYER_ID, QUOTE_ID, 1L, NOW);
    }

    @Test
    void cotizacionAusenteHistoricaInvalidadaOVencidaSeRechaza() {
        givenReadyAndActivePlayer();
        when(versiones.findExactVigente(PLAYER_ID, QUOTE_ID, 1L, NOW))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validar(PLAYER_ID, QUOTE_ID, 1L))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(TokenMarketException.Reason.QUOTE_UNAVAILABLE));
    }

    @Test
    void mismoPrecioNoPermiteUsarQuoteIdOVersionHistoricos() {
        givenReadyAndActivePlayer();
        when(versiones.findExactVigente(PLAYER_ID, QUOTE_ID, 1L, NOW))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validar(PLAYER_ID, QUOTE_ID, 1L))
                .isInstanceOf(TokenMarketException.class);
        verify(versiones).findExactVigente(PLAYER_ID, QUOTE_ID, 1L, NOW);
    }

    @Test
    void entradaInvalidaSeRechazaSinConsultarPersistencia() {
        assertInvalid(null, QUOTE_ID, 1L);
        assertInvalid(PLAYER_ID, null, 1L);
        assertInvalid(PLAYER_ID, QUOTE_ID, 0L);
    }

    private void givenReadyAndActivePlayer() {
        givenReadyState();
        when(jugadores.buscarActivoPorId(PLAYER_ID)).thenReturn(Optional.of(jugadorActivo));
    }

    private void givenReadyState() {
        when(estados.findSingletonForRead()).thenReturn(Optional.of(state(EstadoMercado.READY)));
    }

    private void assertInvalid(UUID playerId, UUID quoteId, long version) {
        assertThatThrownBy(() -> service.validar(playerId, quoteId, version))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(TokenMarketException.Reason.INVALID_INPUT));
    }

    private static EstadoMercadoEntity state(EstadoMercado status) {
        return new EstadoMercadoEntity((short) 1, status.name(), 4L, null, null, null, NOW, NOW);
    }

    private static VersionCotizacionMercadoEntity quoteEntity(QuoteValidityStatus status,
            Instant validUntil) {
        return new VersionCotizacionMercadoEntity(
                new VersionCotizacionMercadoId(QUOTE_ID, 3L), PLAYER_ID,
                new BigDecimal("1.25"), NOW, NOW, validUntil, status.name(),
                "valuation-v1", NOW);
    }
}
