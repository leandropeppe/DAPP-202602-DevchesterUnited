package com.devchesterunited.devchesterunitedv2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.modelo.market.EstadoMercado;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.EstadoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.MarketAvailabilityRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.EstadoMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.projection.MarketPlayerAvailabilityProjection;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketQuoteAvailability;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketAvailabilityServiceImpl;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class MarketAvailabilityServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock private EstadoMercadoRepository estados;
    @Mock private MarketAvailabilityRepository mercado;
    @Mock private MarketPlayerAvailabilityProjection projection;

    private MarketAvailabilityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MarketAvailabilityServiceImpl(
                estados, mercado, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(mercado.findAllForMarket(PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(java.util.List.of(projection), PageRequest.of(0, 20), 1));
    }

    @Test
    void readyPermiteConsultaYJugadorCompletoEsOperable() {
        givenState("READY", 8L);
        givenCompleteCurrentQuote();

        var result = service.consultarJugadores(1, 20);

        assertThat(result.estado()).isEqualTo(EstadoMercado.READY);
        assertThat(result.epoch()).isEqualTo(8L);
        assertThat(result.operativa()).isTrue();
        assertThat(result.jugadores()).singleElement().satisfies(player -> {
            assertThat(player.playerId()).isEqualTo(PLAYER_ID);
            assertThat(player.operable()).isTrue();
            assertThat(player.quoteAvailability()).isEqualTo(MarketQuoteAvailability.CURRENT);
            assertThat(player.inventoryQuantity()).isEqualTo(25L);
        });
    }

    @Test
    void estadosNoReadyImpidendisponibilidadOperativa() {
        for (String status : new String[]{"UNINITIALIZED", "INITIALIZING", "FAILED"}) {
            givenState(status, 3L);
            givenCompleteCurrentQuote();

            var result = service.consultarJugadores(1, 20);

            assertThat(result.estado()).isEqualTo(EstadoMercado.valueOf(status));
            assertThat(result.operativa()).isFalse();
            assertThat(result.jugadores()).singleElement().extracting(p -> p.operable())
                    .isEqualTo(false);
        }
    }

    @Test
    void jugadorActivoSinEmisionNoEsOperable() {
        givenState("READY", 1L);
        givenCompleteCurrentQuote();
        when(projection.getIssuedQuantity()).thenReturn(0L);

        assertThat(service.consultarJugadores(1, 20).jugadores())
                .singleElement().extracting(p -> p.operable()).isEqualTo(false);
    }

    @Test
    void jugadorActivoSinCotizacionEsVisiblePeroNoOperable() {
        givenState("READY", 1L);
        when(projection.getPlayerId()).thenReturn(PLAYER_ID);
        when(projection.getPlayerActive()).thenReturn(true);
        when(projection.getTeamActive()).thenReturn(true);
        when(projection.getIssuedQuantity()).thenReturn(100L);
        when(projection.getInventoryQuantity()).thenReturn(100L);

        var player = service.consultarJugadores(1, 20).jugadores().get(0);

        assertThat(player.quoteAvailability()).isEqualTo(MarketQuoteAvailability.ABSENT);
        assertThat(player.operable()).isFalse();
    }

    @Test
    void consultarJugadorUsaLaProyeccionIndividualYLasMismasReglas() {
        givenState("READY", 1L);
        givenCompleteCurrentQuote();
        when(mercado.findForMarket(PLAYER_ID)).thenReturn(Optional.of(projection));

        var player = service.consultarJugador(PLAYER_ID).orElseThrow();

        assertThat(player.playerId()).isEqualTo(PLAYER_ID);
        assertThat(player.operable()).isTrue();
        assertThat(player.quoteAvailability()).isEqualTo(MarketQuoteAvailability.CURRENT);
        verify(mercado).findForMarket(PLAYER_ID);
    }

    @Test
    void consultarJugadorInexistenteNoInventaUnaDisponibilidad() {
        givenState("READY", 1L);
        when(mercado.findForMarket(PLAYER_ID)).thenReturn(Optional.empty());

        assertThat(service.consultarJugador(PLAYER_ID)).isEmpty();
    }

    @Test
    void cotizacionInicialUnoSinValorizacionRealNoEsOperable() {
        givenState("READY", 1L);
        givenCompleteCurrentQuote();
        when(projection.getCurrentPricePerToken()).thenReturn(new BigDecimal("1.00"));
        when(projection.getCurrentStrategyReference()).thenReturn(null);

        assertThat(service.consultarJugadores(1, 20).jugadores())
                .singleElement().extracting(p -> p.operable()).isEqualTo(false);
    }

    @Test
    void jugadorInactivoNoEsOperableAunqueConserveEmisionYCotizacion() {
        givenState("READY", 1L);
        givenCompleteCurrentQuote();
        when(projection.getPlayerActive()).thenReturn(false);

        assertThat(service.consultarJugadores(1, 20).jugadores())
                .singleElement().extracting(p -> p.operable()).isEqualTo(false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"REPLACED", "INVALIDATED", "EXPIRED"})
    void cotizacionReemplazadaInvalidadaOVencidaNoEsVigente(String status) {
        givenState("READY", 1L);
        givenCompleteCurrentQuote();
        when(projection.getCurrentValidityStatus()).thenReturn(status);
        when(projection.getCurrentPricePerToken()).thenReturn(new BigDecimal("1.25"));

        var player = service.consultarJugadores(1, 20).jugadores().get(0);

        assertThat(player.quoteAvailability()).isEqualTo(MarketQuoteAvailability.HISTORICAL);
        assertThat(player.operable()).isFalse();
        assertThat(player.pricePerToken()).isEqualByComparingTo("1.25");
    }

    @Test
    void inventarioCeroNoEsOperable() {
        givenState("READY", 1L);
        givenCompleteCurrentQuote();
        when(projection.getInventoryQuantity()).thenReturn(0L);

        assertThat(service.consultarJugadores(1, 20).jugadores())
                .singleElement().extracting(p -> p.operable()).isEqualTo(false);
    }

    @Test
    void consultarSoloLeeEstadoYProyeccion() {
        givenState("READY", 1L);
        givenCompleteCurrentQuote();

        service.consultarJugadores(1, 20);

        verify(estados).findSingletonForRead();
        verify(mercado).findAllForMarket(PageRequest.of(0, 20));
        verifyNoMoreInteractions(estados, mercado);
    }

    private void givenState(String status, long epoch) {
        when(estados.findSingletonForRead()).thenReturn(Optional.of(
                new EstadoMercadoEntity((short) 1, status, epoch, null, null,
                        null, NOW, NOW)));
    }

    private void givenCompleteCurrentQuote() {
        when(projection.getPlayerId()).thenReturn(PLAYER_ID);
        when(projection.getPlayerName()).thenReturn("Market Player");
        when(projection.getLeague()).thenReturn("Premier League");
        when(projection.getTeamName()).thenReturn("Market Team");
        when(projection.getPlayerActive()).thenReturn(true);
        when(projection.getTeamActive()).thenReturn(true);
        when(projection.getIssuedQuantity()).thenReturn(100L);
        when(projection.getInventoryQuantity()).thenReturn(25L);
        when(projection.getCurrentQuoteId()).thenReturn(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        when(projection.getCurrentQuoteVersion()).thenReturn(2L);
        when(projection.getCurrentPricePerToken()).thenReturn(new BigDecimal("1.25"));
        when(projection.getCurrentValidityStatus()).thenReturn("VALID");
        when(projection.getCurrentValidFrom()).thenReturn(NOW.minusSeconds(60));
        when(projection.getCurrentValidUntil()).thenReturn(null);
    }
}
