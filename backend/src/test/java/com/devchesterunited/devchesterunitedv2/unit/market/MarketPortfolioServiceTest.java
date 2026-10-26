package com.devchesterunited.devchesterunitedv2.service;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.Jugador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.persistence.market.repository.PnLRealizadoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.TenenciaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.VersionCotizacionMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.PnLRealizadoMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.TenenciaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.TenenciaMercadoId;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.projection.JugadorPortfolioProjection;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.VersionCotizacionMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.mapper.VersionCotizacionMercadoMapper;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketQuoteAvailability;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteValidityStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteVersion;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketPortfolioServiceImpl;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.QuotePublicationPort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketPortfolioServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PLAYER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_PLAYER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID QUOTE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Mock private TenenciaMercadoRepository tenencias;
    @Mock private JugadorRepository jugadores;
    @Mock private PnLRealizadoMercadoRepository pnlRealizado;
    @Mock private QuotePublicationPort cotizaciones;
    @Mock private VersionCotizacionMercadoRepository versiones;
    @Mock private VersionCotizacionMercadoMapper mapper;

    private MarketPortfolioServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MarketPortfolioServiceImpl(
                tenencias,
                pnlRealizado,
                cotizaciones,
                versiones,
                mapper,
                Clock.fixed(NOW, ZoneOffset.UTC),
                jugadores);
    }

    @Test
    void usaCantidadYCostoTotalPendienteParaElPromedioPonderado() {
        givenHoldings(holding(USER_ID, PLAYER_ID, 4L, "10.00000000"));
        givenNoRealizedPnl();
        givenAbsentQuote(PLAYER_ID);

        var result = service.consultar(USER_ID);

        assertThat(result.posicionesAbiertas()).hasSize(1);
        var position = result.posicionesAbiertas().get(0);
        assertThat(position.cantidad()).isEqualTo(4L);
        assertThat(position.costoTotalPendiente()).isEqualByComparingTo("10.00000000");
        assertThat(position.costoPromedioPonderado()).isEqualByComparingTo("2.50000000");
    }

    @Test
    void redondeaDivisionNoExactaAOchoDecimalesConHalfEven() {
        givenHoldings(holding(USER_ID, PLAYER_ID, 3L, "1.00000000"));
        givenNoRealizedPnl();
        givenAbsentQuote(PLAYER_ID);

        var result = service.consultar(USER_ID);

        assertThat(result.posicionesAbiertas()).hasSize(1);
        assertThat(result.posicionesAbiertas().get(0).costoPromedioPonderado())
                .isEqualByComparingTo("0.33333333");
    }

    @Test
    void conservaCantidadYCostoResidualDeUnaVentaParcial() {
        givenHoldings(holding(USER_ID, PLAYER_ID, 2L, "0.66666667"));
        givenNoRealizedPnl();
        givenAbsentQuote(PLAYER_ID);

        var result = service.consultar(USER_ID);
        assertThat(result.posicionesAbiertas()).hasSize(1);
        var position = result.posicionesAbiertas().get(0);

        assertThat(position.cantidad()).isEqualTo(2L);
        assertThat(position.costoTotalPendiente()).isEqualByComparingTo("0.66666667");
        assertThat(position.costoPromedioPonderado()).isEqualByComparingTo("0.33333334");
    }

    @Test
    void excluyePosicionesConCantidadCero() {
        givenHoldings(holding(USER_ID, PLAYER_ID, 0L, "0.00000000"));
        givenNoRealizedPnl();

        var result = service.consultar(USER_ID);

        assertThat(result.posicionesAbiertas()).isEmpty();
        verify(cotizaciones, never()).currentQuote(PLAYER_ID);
        verify(versiones, never()).findAllByJugadorIdOrderByPublicadoEnDesc(PLAYER_ID);
    }

    @Test
    void conservaPnlRealizadaAunqueLaPosicionEsteCerrada() {
        givenHoldings();
        givenRealizedPnl("4.25000000");

        var result = service.consultar(USER_ID);

        assertThat(result.posicionesAbiertas()).isEmpty();
        assertThat(result.pnlRealizado()).isEqualByComparingTo("4.25000000");
        assertThat(result.pnlNoRealizado()).contains(BigDecimal.ZERO.setScale(8));
        assertThat(result.resultadoCombinado()).contains(BigDecimal.valueOf(4.25)
                .setScale(8));
    }

    @Test
    void calculaValorActualConCotizacionCurrent() {
        givenHoldings(holding(USER_ID, PLAYER_ID, 3L, "6.00000000"));
        givenNoRealizedPnl();
        givenCurrentQuote(PLAYER_ID, "3.00");

        var result = service.consultar(USER_ID);
        assertThat(result.posicionesAbiertas()).hasSize(1);
        var position = result.posicionesAbiertas().get(0);

        assertThat(position.disponibilidadCotizacion()).isEqualTo(MarketQuoteAvailability.CURRENT);
        assertThat(position.valorActual()).contains(BigDecimal.valueOf(9).setScale(8));
        assertThat(position.cotizacion()).isPresent();
        assertThat(result.jugadoresAbiertos()).isEqualTo(1L);
        assertThat(result.tokensTotales()).isEqualTo(3L);
        assertThat(result.costoPendienteTotal()).isEqualByComparingTo("6.00000000");
        assertThat(result.valorActualTotal()).contains(BigDecimal.valueOf(9).setScale(8));
    }

    @Test
    void calculaPnlNoRealizadaComoValorActualMenosCostoPendiente() {
        givenHoldings(holding(USER_ID, PLAYER_ID, 2L, "5.00000000"));
        givenNoRealizedPnl();
        givenCurrentQuote(PLAYER_ID, "3.00");

        var result = service.consultar(USER_ID);

        assertThat(result.posicionesAbiertas()).hasSize(1);
        assertThat(result.posicionesAbiertas().get(0).pnlNoRealizado())
                .contains(BigDecimal.ONE.setScale(8));
        assertThat(result.pnlNoRealizado()).contains(BigDecimal.ONE.setScale(8));
    }

    @Test
    void presentaCotizacionHistoricaSinValoracionActual() {
        VersionCotizacionMercadoEntity historicalEntity = org.mockito.Mockito.mock(
                VersionCotizacionMercadoEntity.class);
        QuoteVersion historicalQuote = quote("2.00", QuoteValidityStatus.INVALIDATED);
        givenHoldings(holding(USER_ID, PLAYER_ID, 2L, "3.00000000"));
        givenNoRealizedPnl();
        when(cotizaciones.currentQuote(PLAYER_ID)).thenReturn(Optional.empty());
        when(versiones.findAllByJugadorIdOrderByPublicadoEnDesc(PLAYER_ID))
                .thenReturn(List.of(historicalEntity));
        when(mapper.toDomain(historicalEntity)).thenReturn(historicalQuote);

        var result = service.consultar(USER_ID);
        assertThat(result.posicionesAbiertas()).hasSize(1);
        var position = result.posicionesAbiertas().get(0);

        assertThat(position.disponibilidadCotizacion()).isEqualTo(MarketQuoteAvailability.HISTORICAL);
        assertThat(position.cotizacion()).isPresent();
        assertThat(position.cotizacion().orElseThrow().cotizacionId()).isEqualTo(QUOTE_ID);
        assertThat(position.valorActual()).isEmpty();
        assertThat(position.pnlNoRealizado()).isEmpty();
    }

    @Test
    void manejaCotizacionAbsentSinSnapshotNiValoresDerivados() {
        givenHoldings(holding(USER_ID, PLAYER_ID, 2L, "3.00000000"));
        givenNoRealizedPnl();
        givenAbsentQuote(PLAYER_ID);

        var result = service.consultar(USER_ID);
        assertThat(result.posicionesAbiertas()).hasSize(1);
        var position = result.posicionesAbiertas().get(0);

        assertThat(position.disponibilidadCotizacion()).isEqualTo(MarketQuoteAvailability.ABSENT);
        assertThat(position.cotizacion()).isEmpty();
        assertThat(position.valorActual()).isEmpty();
        assertThat(position.pnlNoRealizado()).isEmpty();
    }

    @Test
    void dejaResultadoCombinadoIncompletoSiFaltaCotizacionVigente() {
        givenHoldings(
                holding(USER_ID, PLAYER_ID, 2L, "2.00000000"),
                holding(USER_ID, OTHER_PLAYER_ID, 1L, "1.00000000"));
        givenNoRealizedPnl();
        givenCurrentQuote(PLAYER_ID, "2.00");
        givenAbsentQuote(OTHER_PLAYER_ID);

        var result = service.consultar(USER_ID);

        assertThat(result.resultadoCombinadoCompleto()).isFalse();
        assertThat(result.pnlNoRealizado()).isEmpty();
        assertThat(result.resultadoCombinado()).isEmpty();
        assertThat(result.posicionesAbiertas()).extracting(p -> p.jugadorId())
                .containsExactlyInAnyOrder(PLAYER_ID, OTHER_PLAYER_ID);
        assertThat(result.jugadoresAbiertos()).isEqualTo(2L);
        assertThat(result.tokensTotales()).isEqualTo(3L);
        assertThat(result.costoPendienteTotal()).isEqualByComparingTo("3.00000000");
        assertThat(result.valorActualTotal()).isEmpty();
    }

    @Test
    void calculaResultadoCombinadoCuandoTodasLasPosicionesTienenCotizacionCurrent() {
        givenHoldings(
                holding(USER_ID, PLAYER_ID, 2L, "2.00000000"),
                holding(USER_ID, OTHER_PLAYER_ID, 1L, "1.00000000"));
        givenRealizedPnl("1.00000000");
        givenCurrentQuote(PLAYER_ID, "2.00");
        givenCurrentQuote(OTHER_PLAYER_ID, "3.00");

        var result = service.consultar(USER_ID);

        assertThat(result.resultadoCombinadoCompleto()).isTrue();
        assertThat(result.pnlNoRealizado())
                .contains(BigDecimal.valueOf(4).setScale(8));
        assertThat(result.resultadoCombinado())
                .contains(BigDecimal.valueOf(5).setScale(8));
        assertThat(result.valorActualTotal()).contains(BigDecimal.valueOf(7).setScale(8));
    }

    @Test
    void portfolioVacioConservaPnlRealizadaYDevuelvePnlNoRealizadaCero() {
        givenHoldings();
        givenRealizedPnl("-2.50000000");

        var result = service.consultar(USER_ID);

        assertThat(result.posicionesAbiertas()).isEmpty();
        assertThat(result.pnlRealizado()).isEqualByComparingTo("-2.50000000");
        assertThat(result.pnlNoRealizado()).contains(BigDecimal.ZERO.setScale(8));
        assertThat(result.resultadoCombinado()).contains(BigDecimal.valueOf(-2.5)
                .setScale(8));
        assertThat(result.resultadoCombinadoCompleto()).isTrue();
    }

    @Test
    void restringeLasConsultasAlUsuarioSolicitado() {
        givenHoldings();
        givenRealizedPnl("1.00000000");

        service.consultar(USER_ID);

        verify(tenencias).findAllByUsuarioId(USER_ID);
        verify(pnlRealizado).findById(USER_ID);
        verify(tenencias, never()).findAllByUsuarioId(OTHER_USER_ID);
        verify(pnlRealizado, never()).findById(OTHER_USER_ID);
    }

    @Test
    void enriqueceLaPosicionConDatosCanonicosSinExigirQueElJugadorEsteActivo() {
        givenHoldings(holding(USER_ID, PLAYER_ID, 2L, "3.00000000"));
        givenNoRealizedPnl();
        givenAbsentQuote(PLAYER_ID);
        JugadorPortfolioProjection projection = org.mockito.Mockito.mock(
                JugadorPortfolioProjection.class);
        when(projection.getPlayerId()).thenReturn(PLAYER_ID);
        when(projection.getPlayerName()).thenReturn("Kenny Tete");
        when(projection.getTeam()).thenReturn("Fulham");
        when(projection.getLeague()).thenReturn("Premier League");
        when(jugadores.findPortfolioDescriptions(List.of(PLAYER_ID)))
                .thenReturn(List.of(projection));

        var result = service.consultar(USER_ID);

        assertThat(result.posicionesAbiertas().get(0).nombreJugador()).isEqualTo("Kenny Tete");
        assertThat(result.posicionesAbiertas().get(0).equipo()).isEqualTo("Fulham");
        assertThat(result.posicionesAbiertas().get(0).liga()).isEqualTo("Premier League");
    }

    @Test
    void conservaLaPosicionCuandoLaDescripcionCanonicaEsIncompleta() {
        givenHoldings(holding(USER_ID, PLAYER_ID, 1L, "1.00000000"));
        givenNoRealizedPnl();
        givenAbsentQuote(PLAYER_ID);
        JugadorPortfolioProjection projection = org.mockito.Mockito.mock(
                JugadorPortfolioProjection.class);
        when(projection.getPlayerId()).thenReturn(PLAYER_ID);
        when(projection.getPlayerName()).thenReturn("Jugador sin equipo");
        when(jugadores.findPortfolioDescriptions(List.of(PLAYER_ID)))
                .thenReturn(List.of(projection));

        var result = service.consultar(USER_ID);

        assertThat(result.posicionesAbiertas()).hasSize(1);
        assertThat(result.posicionesAbiertas().get(0).nombreJugador())
                .isEqualTo("Jugador sin equipo");
        assertThat(result.posicionesAbiertas().get(0).equipo()).isNull();
        assertThat(result.posicionesAbiertas().get(0).liga()).isNull();
    }

    @Test
    void rechazaUsuarioIdNulo() {
        assertThatThrownBy(() -> service.consultar(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("El usuario del portfolio es obligatorio.");
    }

    private void givenHoldings(TenenciaMercadoEntity... holdings) {
        when(tenencias.findAllByUsuarioId(USER_ID)).thenReturn(List.of(holdings));
    }

    private void givenNoRealizedPnl() {
        when(pnlRealizado.findById(USER_ID)).thenReturn(Optional.empty());
    }

    private void givenRealizedPnl(String amount) {
        when(pnlRealizado.findById(USER_ID)).thenReturn(Optional.of(
                new PnLRealizadoMercadoEntity(USER_ID, new BigDecimal(amount), NOW)));
    }

    private void givenAbsentQuote(UUID playerId) {
        when(cotizaciones.currentQuote(playerId)).thenReturn(Optional.empty());
        when(versiones.findAllByJugadorIdOrderByPublicadoEnDesc(playerId))
                .thenReturn(List.of());
    }

    private void givenCurrentQuote(UUID playerId, String price) {
        when(cotizaciones.currentQuote(playerId)).thenReturn(Optional.of(
                quote(playerId, price, QuoteValidityStatus.VALID)));
    }

    private TenenciaMercadoEntity holding(UUID userId, UUID playerId, long quantity,
            String cost) {
        return new TenenciaMercadoEntity(
                new TenenciaMercadoId(userId, playerId),
                null,
                null,
                quantity,
                new BigDecimal(cost),
                NOW);
    }

    private QuoteVersion quote(String price, QuoteValidityStatus status) {
        return quote(PLAYER_ID, price, status);
    }

    private QuoteVersion quote(UUID playerId, String price, QuoteValidityStatus status) {
        return new QuoteVersion(
                QUOTE_ID,
                1L,
                playerId,
                new BigDecimal(price),
                NOW.minusSeconds(10),
                NOW.minusSeconds(5),
                null,
                status,
                "test");
    }
}
