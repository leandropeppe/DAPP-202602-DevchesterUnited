package com.devchesterunited.devchesterunitedv2.integration.market.controller;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PQuoteIdentity;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "token-market.initialization.auto-start=false")
@AutoConfigureMockMvc
class MarketP2PPurchaseControllerTest extends PostgresTestSupport {
    private static final UUID BUYER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID SELLER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID OFFER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID QUOTE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OPERATION_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID TRADE_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MarketP2PPurchaseService purchases;

    @Test
    void compraParcialPropagaJwtOfertaCotizacionYKey() throws Exception {
        when(purchases.comprar(any(UUID.class), anyString(), eq(OFFER_ID), anyLong(),
                any(MarketP2PQuoteIdentity.class))).thenReturn(result(2L, 2L, 2L, "CONFIRMED"));

        mvc.perform(post(path())
                        .with(jwtFor(BUYER_ID))
                        .header("Idempotency-Key", " p2p-partial ")
                        .contentType("application/json")
                        .content(body(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.operation_id").value(OPERATION_ID.toString()))
                .andExpect(jsonPath("$.data.trade_id").value(TRADE_ID.toString()))
                .andExpect(jsonPath("$.data.offer_id").value(OFFER_ID.toString()))
                .andExpect(jsonPath("$.data.player_id").value(PLAYER_ID.toString()))
                .andExpect(jsonPath("$.data.quantity").value(2))
                .andExpect(jsonPath("$.data.quote_id").value(QUOTE_ID.toString()))
                .andExpect(jsonPath("$.data.quote_version").value(1))
                .andExpect(jsonPath("$.data.unit_price").value(2.50))
                .andExpect(jsonPath("$.data.total_amount").value(5.00))
                .andExpect(jsonPath("$.data.remaining_quantity").value(2))
                .andExpect(jsonPath("$.data.reserved_quantity").value(2))
                .andExpect(jsonPath("$.data.status").value("PARTIALLY_FILLED"))
                .andExpect(jsonPath("$.data.operation_status").value("CONFIRMED"));

        var user = org.mockito.ArgumentCaptor.forClass(UUID.class);
        var key = org.mockito.ArgumentCaptor.forClass(String.class);
        var offer = org.mockito.ArgumentCaptor.forClass(UUID.class);
        var quantity = org.mockito.ArgumentCaptor.forClass(Long.class);
        var quote = org.mockito.ArgumentCaptor.forClass(MarketP2PQuoteIdentity.class);
        verify(purchases).comprar(user.capture(), key.capture(), offer.capture(), quantity.capture(),
                quote.capture());
        assertThat(user.getValue()).isEqualTo(BUYER_ID);
        assertThat(key.getValue()).isEqualTo("p2p-partial");
        assertThat(offer.getValue()).isEqualTo(OFFER_ID);
        assertThat(quantity.getValue()).isEqualTo(2L);
        assertThat(quote.getValue().quoteId()).isEqualTo(QUOTE_ID);
        assertThat(quote.getValue().version()).isEqualTo(1L);
    }

    @Test
    void compraCompletaExponeEstadoDeOperacionConfirmadaYOfertaCompleta() throws Exception {
        when(purchases.comprar(any(UUID.class), anyString(), eq(OFFER_ID), anyLong(),
                any(MarketP2PQuoteIdentity.class))).thenReturn(result(4L, 0L, 0L, "CONFIRMED"));

        mvc.perform(post(path()).with(jwtFor(BUYER_ID))
                        .header("Idempotency-Key", "p2p-full")
                        .contentType("application/json").content(body(4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(4))
                .andExpect(jsonPath("$.data.remaining_quantity").value(0))
                .andExpect(jsonPath("$.data.reserved_quantity").value(0))
                .andExpect(jsonPath("$.data.status").value("FILLED"))
                .andExpect(jsonPath("$.data.operation_status").value("CONFIRMED"));
    }

    @Test
    void replayDevuelveElMismoResultadoSinCambiarElEnvelope() throws Exception {
        when(purchases.comprar(any(UUID.class), anyString(), eq(OFFER_ID), anyLong(),
                any(MarketP2PQuoteIdentity.class))).thenReturn(result(2L, 2L, 2L, "CONFIRMED"));

        mvc.perform(post(path()).with(jwtFor(BUYER_ID)).header("Idempotency-Key", "same-key")
                .contentType("application/json").content(body(2))).andExpect(status().isOk());
        mvc.perform(post(path()).with(jwtFor(BUYER_ID)).header("Idempotency-Key", "same-key")
                .contentType("application/json").content(body(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trade_id").value(TRADE_ID.toString()));

        verify(purchases, times(2)).comprar(any(UUID.class), anyString(), eq(OFFER_ID), anyLong(),
                any(MarketP2PQuoteIdentity.class));
    }

    @Test
    void conflictoDeIdempotenciaSeExponeComo409ConCodigoDeNegocio() throws Exception {
        when(purchases.comprar(any(UUID.class), anyString(), eq(OFFER_ID), anyLong(),
                any(MarketP2PQuoteIdentity.class))).thenThrow(new TokenMarketException(
                TokenMarketException.Reason.IDEMPOTENCY_CONFLICT, "conflict"));

        mvc.perform(post(path()).with(jwtFor(BUYER_ID)).header("Idempotency-Key", "reused")
                        .contentType("application/json").content(body(2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void erroresDeNegocioP2PConservan409YCodigo() throws Exception {
        when(purchases.comprar(any(UUID.class), anyString(), eq(OFFER_ID), anyLong(),
                any(MarketP2PQuoteIdentity.class))).thenThrow(new TokenMarketException(
                TokenMarketException.Reason.INSUFFICIENT_BALANCE, "insufficient"));

        mvc.perform(post(path()).with(jwtFor(BUYER_ID)).header("Idempotency-Key", "low-balance")
                        .contentType("application/json").content(body(2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_CREDITS"));

        doThrow(new TokenMarketException(
                TokenMarketException.Reason.OFFER_NOT_PURCHASABLE, "own-offer"))
                .when(purchases).comprar(any(UUID.class), anyString(), eq(OFFER_ID), anyLong(),
                        any(MarketP2PQuoteIdentity.class));

        mvc.perform(post(path()).with(jwtFor(SELLER_ID)).header("Idempotency-Key", "own-offer")
                        .contentType("application/json").content(body(1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OFFER_NOT_PURCHASABLE"));

        doThrow(new TokenMarketException(
                TokenMarketException.Reason.INSUFFICIENT_AVAILABLE_QUANTITY, "too-many"))
                .when(purchases).comprar(any(UUID.class), anyString(), eq(OFFER_ID), anyLong(),
                        any(MarketP2PQuoteIdentity.class));
        mvc.perform(post(path()).with(jwtFor(BUYER_ID)).header("Idempotency-Key", "too-many")
                        .contentType("application/json").content(body(5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_AVAILABLE_QUANTITY"));
    }

    @Test
    void quoteChangedIncluyeLaCotizacionVigente() throws Exception {
        QuoteVersion current = new QuoteVersion(QUOTE_ID, 2L, PLAYER_ID,
                new BigDecimal("3.00"), INSTANT, INSTANT, null, QuoteValidityStatus.VALID, "TEST");
        when(purchases.comprar(any(UUID.class), anyString(), eq(OFFER_ID), anyLong(),
                any(MarketP2PQuoteIdentity.class))).thenThrow(new TokenMarketException(
                TokenMarketException.Reason.QUOTE_CHANGED, "changed", current));

        mvc.perform(post(path()).with(jwtFor(BUYER_ID)).header("Idempotency-Key", "quote-changed")
                        .contentType("application/json").content(body(2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUOTE_CHANGED"))
                .andExpect(jsonPath("$.current_quote.quote_id").value(QUOTE_ID.toString()))
                .andExpect(jsonPath("$.current_quote.quote_version").value(2))
                .andExpect(jsonPath("$.current_quote.unit_price").value(3.00));
    }

    @Test
    void quoteUnavailableExponeNullYOfertaSuspendida() throws Exception {
        when(purchases.comprar(any(UUID.class), anyString(), eq(OFFER_ID), anyLong(),
                any(MarketP2PQuoteIdentity.class))).thenThrow(new TokenMarketException(
                TokenMarketException.Reason.QUOTE_UNAVAILABLE, "unavailable"));

        mvc.perform(post(path()).with(jwtFor(BUYER_ID)).header("Idempotency-Key", "quote-unavailable")
                        .contentType("application/json").content(body(2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUOTE_UNAVAILABLE"))
                .andExpect(jsonPath("$.current_quote").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.offer_status").value("SUSPENDED"));
    }

    @Test
    void replayHistoricoSinSnapshotExponeConflictoPublico() throws Exception {
        when(purchases.comprar(any(UUID.class), anyString(), eq(OFFER_ID), anyLong(),
                any(MarketP2PQuoteIdentity.class))).thenThrow(new TokenMarketException(
                TokenMarketException.Reason.HISTORICAL_RESULT_UNAVAILABLE, "legacy"));

        mvc.perform(post(path()).with(jwtFor(BUYER_ID)).header("Idempotency-Key", "legacy-replay")
                        .contentType("application/json").content(body(1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HISTORICAL_RESULT_UNAVAILABLE"));
    }

    @Test
    void jwtAusenteOInvalidoNoInvocaElServicio() throws Exception {
        mvc.perform(post(path()).header("Idempotency-Key", "no-jwt")
                        .contentType("application/json").content(body(1)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(path()).with(jwt().jwt(token -> token.subject("invalid")))
                        .header("Idempotency-Key", "bad-jwt")
                        .contentType("application/json").content(body(1)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(purchases);
    }

    @Test
    void bodyInvalidoYCamposDeIdentidadNoInvocanElServicio() throws Exception {
        mvc.perform(post(path()).with(jwtFor(BUYER_ID)).header("Idempotency-Key", "bad-body")
                        .contentType("application/json")
                        .content("{\"quantity\":0,\"quote_id\":\"bad\",\"quote_version\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(path()).with(jwtFor(BUYER_ID)).header("Idempotency-Key", "user-field")
                        .contentType("application/json")
                        .content("{\"quantity\":1,\"quote_id\":\"" + QUOTE_ID
                                + "\",\"quote_version\":1,\"user_id\":\"" + SELLER_ID + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(path()).with(jwtFor(BUYER_ID)).header("Idempotency-Key", "player-field")
                        .contentType("application/json")
                        .content("{\"quantity\":1,\"quote_id\":\"" + QUOTE_ID
                                + "\",\"quote_version\":1,\"player_id\":\"" + PLAYER_ID + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(purchases);
    }

    @Test
    void keyAusenteOfertaInexistenteYUuidInvalidoRechazanLaSolicitud() throws Exception {
        mvc.perform(post(path()).with(jwtFor(BUYER_ID)).contentType("application/json")
                        .content(body(1))).andExpect(status().isBadRequest());
        verifyNoInteractions(purchases);

        when(purchases.comprar(any(UUID.class), anyString(), eq(OFFER_ID), anyLong(),
                any(MarketP2PQuoteIdentity.class))).thenThrow(new TokenMarketException(
                        TokenMarketException.Reason.OFFER_NOT_FOUND, "missing"));
        mvc.perform(post(path()).with(jwtFor(BUYER_ID)).header("Idempotency-Key", "missing-offer")
                        .contentType("application/json").content(body(1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OFFER_NOT_FOUND"));
        verify(purchases).comprar(any(UUID.class), anyString(), eq(OFFER_ID), anyLong(),
                any(MarketP2PQuoteIdentity.class));

        mvc.perform(post("/api/v1/market/p2p/offers/not-a-uuid/purchases")
                        .with(jwtFor(BUYER_ID)).header("Idempotency-Key", "bad-uuid")
                        .contentType("application/json").content(body(1)))
                .andExpect(status().isBadRequest());
    }

    private static MarketP2PPurchaseResult result(long quantity, long remaining,
            long reserved, String status) {
        return new MarketP2PPurchaseResult(OPERATION_ID, TRADE_ID, OFFER_ID, BUYER_ID,
                new MarketP2PPurchaseResult.Settlement(SELLER_ID, PLAYER_ID, quantity,
                        new MarketP2PPurchaseResult.Quote(QUOTE_ID, 1L, new BigDecimal("2.50")),
                        new MarketP2PPurchaseResult.Quantities(quantity, remaining, reserved,
                                remaining == 0 ? "FILLED" : "PARTIALLY_FILLED"),
                        new BigDecimal("5.00")),
                MarketIdempotencyStatus.valueOf(status));
    }

    private static String path() {
        return "/api/v1/market/p2p/offers/" + OFFER_ID + "/purchases";
    }

    private static String body(long quantity) {
        return "{\"quantity\":" + quantity + ",\"quote_id\":\"" + QUOTE_ID
                + "\",\"quote_version\":1}";
    }

    private static RequestPostProcessor jwtFor(UUID userId) {
        return jwt().jwt(token -> token.subject("usr_" + userId));
    }

    private static final Instant INSTANT = Instant.parse("2026-10-30T12:00:00Z");
}
