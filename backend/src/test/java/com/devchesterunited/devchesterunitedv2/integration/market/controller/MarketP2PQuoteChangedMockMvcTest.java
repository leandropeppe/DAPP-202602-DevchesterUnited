package com.devchesterunited.devchesterunitedv2.integration.market.controller;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.OfertaVentaP2PEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.OfertaVentaP2PRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteVersion;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteValidityStatus;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2PPurchaseService;
import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "token-market.initialization.auto-start=false")
@AutoConfigureMockMvc
class MarketP2PQuoteChangedMockMvcTest extends PostgresTestSupport {
    private static final UUID BUYER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID SELLER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID OFFER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID QUOTE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OPERATION_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID TRADE_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MarketP2PPurchaseService purchases;

    @MockitoBean
    private OfertaVentaP2PRepository offers;

    @Test
    void quoteChangedDevuelve409YLaCotizacionVigenteSinLiquidar() throws Exception {
        stubOffer();
        QuoteVersion current = currentQuote();
        when(purchases.comprar(any())).thenThrow(new TokenMarketException(
                TokenMarketException.Reason.QUOTE_CHANGED, "changed", current));

        mvc.perform(post(path()).with(jwtFor(BUYER_ID))
                        .header("Idempotency-Key", "quote-old-key")
                        .contentType("application/json").content(body(2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").value("QUOTE_CHANGED"))
                .andExpect(jsonPath("$.current_quote.quote_id").value(QUOTE_ID.toString()))
                .andExpect(jsonPath("$.current_quote.quote_version").value(2))
                .andExpect(jsonPath("$.current_quote.unit_price").value(3.00));

        verify(purchases).comprar(any());
    }

    @Test
    void quoteUnavailableDevuelveNullYSuspendedSinConfirmarCompra() throws Exception {
        stubOffer();
        when(purchases.comprar(any())).thenThrow(new TokenMarketException(
                TokenMarketException.Reason.QUOTE_UNAVAILABLE, "unavailable"));

        mvc.perform(post(path()).with(jwtFor(BUYER_ID))
                        .header("Idempotency-Key", "quote-unavailable-key")
                        .contentType("application/json").content(body(2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUOTE_UNAVAILABLE"))
                .andExpect(jsonPath("$.current_quote").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.offer_status").value("SUSPENDED"));
        verify(purchases).comprar(any());
    }

    @Test
    void cambioExigeNuevaClaveYLaSegundaConfirmacionEsIndependiente() throws Exception {
        stubOffer();
        when(purchases.comprar(any()))
                .thenThrow(new TokenMarketException(TokenMarketException.Reason.QUOTE_CHANGED,
                        "changed", currentQuote()))
                .thenReturn(result());

        mvc.perform(post(path()).with(jwtFor(BUYER_ID))
                        .header("Idempotency-Key", "old-key")
                        .contentType("application/json").content(body(2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUOTE_CHANGED"));
        mvc.perform(post(path()).with(jwtFor(BUYER_ID))
                        .header("Idempotency-Key", "new-key")
                        .contentType("application/json").content(body(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trade_id").value(TRADE_ID.toString()))
                .andExpect(jsonPath("$.data.operation_status").value("CONFIRMED"));

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseCommand.class);
        org.mockito.Mockito.verify(purchases, org.mockito.Mockito.times(2)).comprar(captor.capture());
        assertThat(captor.getAllValues().get(0).claveIdempotencia()).isEqualTo("old-key");
        assertThat(captor.getAllValues().get(1).claveIdempotencia()).isEqualTo("new-key");
    }

    private void stubOffer() {
        OfertaVentaP2PEntity offer = org.mockito.Mockito.mock(OfertaVentaP2PEntity.class);
        doReturn(PLAYER_ID).when(offer).getJugadorId();
        when(offers.findById(OFFER_ID)).thenReturn(Optional.of(offer));
    }

    private static QuoteVersion currentQuote() {
        return new QuoteVersion(QUOTE_ID, 2L, PLAYER_ID, new BigDecimal("3.00"),
                NOW, NOW, null, QuoteValidityStatus.VALID, "P2P_TEST");
    }

    private static MarketP2PPurchaseResult result() {
        return new MarketP2PPurchaseResult(OPERATION_ID, TRADE_ID, OFFER_ID, BUYER_ID,
                new MarketP2PPurchaseResult.Settlement(SELLER_ID, PLAYER_ID, 2L,
                        new MarketP2PPurchaseResult.Quote(QUOTE_ID, 2L, new BigDecimal("3.00")),
                        new MarketP2PPurchaseResult.Quantities(2L, 0L, 0L, "FILLED"),
                        new BigDecimal("6.00")), MarketIdempotencyStatus.CONFIRMED);
    }

    private static String path() {
        return "/api/v1/market/p2p/offers/" + OFFER_ID + "/purchases";
    }

    private static String body(long quantity) {
        return "{\"quantity\":" + quantity + ",\"quote_id\":\"" + QUOTE_ID
                + "\",\"quote_version\":1}";
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(
            UUID userId) {
        return jwt().jwt(token -> token.subject("usr_" + userId));
    }
}
