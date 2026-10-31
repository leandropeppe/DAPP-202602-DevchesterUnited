package com.devchesterunited.devchesterunitedv2.unit.market;

import com.devchesterunited.devchesterunitedv2.persistence.market.projection.MarketP2POfferProjection;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.OfertaVentaP2PRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2POfferPageResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2POfferQuery;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2POfferView;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteValidityStatus;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketP2POfferQueryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketP2POfferQueryTest {
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID SELLER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    @Mock private OfertaVentaP2PRepository offers;
    private MarketP2POfferQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MarketP2POfferQueryServiceImpl(offers, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void calculaEstadoDerivadoYCensuraDatosPrivadosDelVendedor() {
        MarketP2POfferProjection offer = projection(UUID.randomUUID(), SELLER_ID,
                "seller-public", "OPEN", true, true);
        when(offers.buscarPublico(null, null, false, USER_ID, NOW, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(offer), PageRequest.of(0, 20), 1));

        MarketP2POfferView view = service.consultar(
                new MarketP2POfferQuery(USER_ID, null, null, false, 1, 20)).offers().get(0);

        assertThat(view.identity().sellerAlias()).isEqualTo("seller-public");
        assertThat(view.identity().mine()).isFalse();
        assertThat(view.availability().status()).isEqualTo("PURCHASABLE");
        assertThat(view.availability().purchasable()).isTrue();
        assertThat(view.quote().unitPrice()).isEqualByComparingTo("2.50");
    }

    @Test
    void jugadorInactivoOQuoteInvalidaDejaLaOfertaSuspendida() {
        MarketP2POfferProjection offer = projection(UUID.randomUUID(), SELLER_ID,
                "seller-public", "OPEN", false, false);
        when(offers.buscarPublico(null, "SUSPENDED", false, USER_ID, NOW,
                PageRequest.of(0, 20))).thenReturn(new PageImpl<>(List.of(offer),
                        PageRequest.of(0, 20), 1));

        MarketP2POfferView view = service.consultar(
                new MarketP2POfferQuery(USER_ID, null, "SUSPENDED", false, 1, 20))
                .offers().get(0);

        assertThat(view.availability().status()).isEqualTo("SUSPENDED");
        assertThat(view.availability().purchasable()).isFalse();
        assertThat(view.quote()).isNull();
    }

    @Test
    void mineSePropagaAlRepositorioConLaIdentidadAutenticada() {
        MarketP2POfferProjection offer = projection(UUID.randomUUID(), USER_ID,
                "my-alias", "PARTIALLY_FILLED", true, true);
        when(offers.buscarPublico(PLAYER_ID, "PARTIALLY_FILLED", true, USER_ID, NOW,
                PageRequest.of(1, 5))).thenReturn(new PageImpl<>(List.of(offer),
                        PageRequest.of(1, 5), 6));

        MarketP2POfferPageResult result = service.consultar(
                new MarketP2POfferQuery(USER_ID, PLAYER_ID, "PARTIALLY_FILLED", true, 2, 5));

        assertThat(result.offers()).hasSize(1);
        assertThat(result.offers().get(0).identity().mine()).isTrue();
        assertThat(result.pagination().paginaActual()).isEqualTo(2);
        verify(offers).buscarPublico(PLAYER_ID, "PARTIALLY_FILLED", true, USER_ID, NOW,
                PageRequest.of(1, 5));
    }

    @Test
    void aplicaPaginacionYConteoEnBaseSinCargarTodasLasOfertas() {
        MarketP2POfferProjection newest = projection(UUID.randomUUID(), SELLER_ID,
                "seller", "OPEN", true, true);
        MarketP2POfferProjection second = projection(UUID.randomUUID(), SELLER_ID,
                "seller", "OPEN", true, true);
        when(offers.buscarPublico(null, "PURCHASABLE", false, USER_ID, NOW,
                PageRequest.of(0, 2))).thenReturn(new PageImpl<>(List.of(newest, second),
                        PageRequest.of(0, 2), 5));

        MarketP2POfferPageResult result = service.consultar(
                new MarketP2POfferQuery(USER_ID, null, "PURCHASABLE", false, 1, 2));

        assertThat(result.offers()).hasSize(2);
        assertThat(result.pagination().totalRegistros()).isEqualTo(5);
        assertThat(result.pagination().totalPaginas()).isEqualTo(3);
        verify(offers).buscarPublico(null, "PURCHASABLE", false, USER_ID, NOW,
                PageRequest.of(0, 2));
    }

    private MarketP2POfferProjection projection(UUID offerId, UUID sellerId, String alias,
            String status, boolean playerActive, boolean quoteValid) {
        MarketP2POfferProjection projection = mock(MarketP2POfferProjection.class);
        UUID quoteId = UUID.randomUUID();
        when(projection.getOfferId()).thenReturn(offerId);
        when(projection.getPlayerId()).thenReturn(PLAYER_ID);
        when(projection.getSellerAlias()).thenReturn(alias);
        when(projection.getRemainingQuantity()).thenReturn(3L);
        when(projection.getPersistedStatus()).thenReturn(status);
        when(projection.getPlayerActive()).thenReturn(playerActive);
        if (playerActive && quoteValid) {
            when(projection.getHeaderValidityStatus()).thenReturn("VALID");
        }
        if (playerActive && quoteValid) {
            when(projection.getSellerId()).thenReturn(sellerId);
        }
        if (quoteValid) {
            when(projection.getQuoteId()).thenReturn(quoteId);
            when(projection.getQuoteVersion()).thenReturn(1L);
            when(projection.getUnitPrice()).thenReturn(new BigDecimal("2.50"));
            when(projection.getPublishedAt()).thenReturn(NOW.minusSeconds(120));
            when(projection.getValidFrom()).thenReturn(NOW.minusSeconds(60));
            when(projection.getQuoteValidityStatus()).thenReturn(QuoteValidityStatus.VALID.name());
            when(projection.getStrategyReference()).thenReturn("official");
        }
        return projection;
    }
}
