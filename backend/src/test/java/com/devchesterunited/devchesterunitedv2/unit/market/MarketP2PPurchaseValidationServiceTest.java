package com.devchesterunited.devchesterunitedv2.unit.market;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.CabeceraCotizacionMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.EstadoMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.OfertaVentaP2PEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.VersionCotizacionMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.VersionCotizacionMercadoId;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.CabeceraCotizacionMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.EstadoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.OfertaVentaP2PRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.VersionCotizacionMercadoRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseCommand;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PQuoteIdentity;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketP2PPurchaseValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketP2PPurchaseValidationServiceTest {
    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID BUYER = UUID.randomUUID();
    private static final UUID OFFER = UUID.randomUUID();
    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID QUOTE = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private OfertaVentaP2PRepository offers;
    private MarketP2PPurchaseValidationService service;

    @BeforeEach
    void setUp() {
        EstadoMercadoRepository states = mock(EstadoMercadoRepository.class);
        offers = mock(OfertaVentaP2PRepository.class);
        var players = mock(com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository.class);
        CabeceraCotizacionMercadoRepository headers = mock(CabeceraCotizacionMercadoRepository.class);
        VersionCotizacionMercadoRepository versions = mock(VersionCotizacionMercadoRepository.class);
        EstadoMercadoEntity state = mock(EstadoMercadoEntity.class);
        when(state.getStatus()).thenReturn("READY");
        when(states.findSingletonForRead()).thenReturn(Optional.of(state));
        when(headers.findByJugadorIdForUpdate(PLAYER)).thenReturn(Optional.of(
                new CabeceraCotizacionMercadoEntity(PLAYER, QUOTE, 1L, NOW)));
        when(versions.findExact(PLAYER, QUOTE, 1L)).thenReturn(Optional.of(
                new VersionCotizacionMercadoEntity(new VersionCotizacionMercadoId(QUOTE, 1L),
                        PLAYER, new BigDecimal("2.50"), NOW, NOW, null, "VALID", "TEST", NOW)));
        when(players.findByIdForUpdate(PLAYER)).thenReturn(Optional.of(new JugadorEntity(PLAYER,
                "Player", UUID.randomUUID(), "MID", 10, "AR", true)));
        service = new MarketP2PPurchaseValidationService(states, offers, players, headers,
                versions, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void calculaSettlementParcialYUsaPrecioOficial() {
        givenOffer(4L, SELLER, OfertaVentaP2PEntity.OPEN);
        var result = service.validar(command(BUYER, 2L, QUOTE, 1L), UUID.randomUUID());
        assertThat(result.quantity()).isEqualTo(2L);
        assertThat(result.quote().pricePerToken()).isEqualByComparingTo("2.50");
    }

    @Test
    void aceptaCompraCompletaYRechazaOfertaPropia() {
        givenOffer(4L, SELLER, OfertaVentaP2PEntity.OPEN);
        assertThat(service.validar(command(BUYER, 4L, QUOTE, 1L), UUID.randomUUID()).quantity())
                .isEqualTo(4L);
        assertThatThrownBy(() -> service.validar(command(SELLER, 1L, QUOTE, 1L), UUID.randomUUID()))
                .isInstanceOfSatisfying(TokenMarketException.class, error ->
                        assertThat(error.reason()).isEqualTo(TokenMarketException.Reason.OFFER_NOT_PURCHASABLE));
    }

    @Test
    void rechazaCantidadSuperiorYQuoteDistinta() {
        givenOffer(4L, SELLER, OfertaVentaP2PEntity.OPEN);
        assertThatThrownBy(() -> service.validar(command(BUYER, 5L, QUOTE, 1L), UUID.randomUUID()))
                .isInstanceOfSatisfying(TokenMarketException.class, error ->
                        assertThat(error.reason()).isEqualTo(TokenMarketException.Reason.INSUFFICIENT_AVAILABLE_QUANTITY));
        assertThatThrownBy(() -> service.validar(command(BUYER, 1L, UUID.randomUUID(), 1L), UUID.randomUUID()))
                .isInstanceOfSatisfying(TokenMarketException.class, error ->
                        assertThat(error.reason()).isEqualTo(TokenMarketException.Reason.QUOTE_CHANGED));
    }

    private void givenOffer(long remaining, UUID seller, String status) {
        OfertaVentaP2PEntity offer = mock(OfertaVentaP2PEntity.class);
        when(offer.getId()).thenReturn(OFFER);
        when(offer.getVendedorId()).thenReturn(seller);
        when(offer.getJugadorId()).thenReturn(PLAYER);
        when(offer.getCantidadRestante()).thenReturn(remaining);
        when(offer.getEstado()).thenReturn(status);
        when(offers.findByIdForUpdate(OFFER)).thenReturn(Optional.of(offer));
    }

    private MarketP2PPurchaseCommand command(UUID buyer, long quantity, UUID quote, long version) {
        return new MarketP2PPurchaseCommand(buyer, "unit-key", OFFER, PLAYER, quantity,
                new MarketP2PQuoteIdentity(quote, version));
    }
}
