package com.devchesterunited.devchesterunitedv2.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPurchaseRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPurchaseResult;
import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketPurchaseService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = "token-market.initialization.auto-start=false")
@AutoConfigureMockMvc
class MarketPurchaseMockMvcTest extends PostgresTestSupport {
    private static final UUID USER_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID QUOTE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OPERATION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID TRANSACTION_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant UPDATED_AT = Instant.parse("2026-10-15T12:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MarketPurchaseService purchases;

    @Test
    void compraConfirmadaDevuelveRespuestaPublicaYDelegaUnaSolaVez() throws Exception {
        when(purchases.comprar(any())).thenReturn(confirmed());

        mvc.perform(post("/api/v1/market/purchases")
                        .with(jwtFor(USER_A))
                        .header("Idempotency-Key", " purchase-1 ")
                        .contentType("application/json")
                        .content(validBody(3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.operation_id").value(OPERATION_ID.toString()))
                .andExpect(jsonPath("$.data.transaction_id").value(TRANSACTION_ID.toString()))
                .andExpect(jsonPath("$.data.player_id").value(PLAYER_ID.toString()))
                .andExpect(jsonPath("$.data.quantity").value(3))
                .andExpect(jsonPath("$.data.unit_price").value(1.25))
                .andExpect(jsonPath("$.data.total_amount").value(3.75))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.user_id").doesNotExist())
                .andExpect(jsonPath("$.data.execution_token").doesNotExist())
                .andExpect(jsonPath("$.data.parameters_hash").doesNotExist());

        var request = org.mockito.ArgumentCaptor.forClass(MarketPurchaseRequest.class);
        verify(purchases).comprar(request.capture());
        assertThat(request.getValue().usuarioId()).isEqualTo(USER_A);
        assertThat(request.getValue().claveIdempotencia()).isEqualTo("purchase-1");
    }

    @Test
    void operacionPendienteDevuelve200SinImportesNiTransaccionInexistentes() throws Exception {
        when(purchases.comprar(any())).thenReturn(new MarketPurchaseResult(
                OPERATION_ID, null, USER_A, PLAYER_ID, null, null, null,
                MarketIdempotencyStatus.PROCESSING, UPDATED_AT));

        mvc.perform(post("/api/v1/market/purchases")
                        .with(jwtFor(USER_A))
                        .header("Idempotency-Key", "purchase-pending")
                        .contentType("application/json")
                        .content(validBody(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.transaction_id").doesNotExist())
                .andExpect(jsonPath("$.data.unit_price").doesNotExist())
                .andExpect(jsonPath("$.data.total_amount").doesNotExist());

        verify(purchases).comprar(any(MarketPurchaseRequest.class));
    }

    @Test
    void compraSinJwtDevuelve401YSinEjecutarCompra() throws Exception {
        mvc.perform(post("/api/v1/market/purchases")
                        .header("Idempotency-Key", "purchase-no-auth")
                        .contentType("application/json")
                        .content(validBody(1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));

        verifyNoInteractions(purchases);
    }

    @Test
    void bearerInvalidoEsRechazadoPorLaCadenaDeSeguridad() throws Exception {
        mvc.perform(post("/api/v1/market/purchases")
                        .header("Authorization", "Bearer invalid-token")
                        .header("Idempotency-Key", "purchase-invalid-token")
                        .contentType("application/json")
                        .content(validBody(1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));

        verifyNoInteractions(purchases);
    }

    @Test
    void sujetoJwtMalFormadoEsRechazadoAntesDeLaCompra() throws Exception {
        mvc.perform(post("/api/v1/market/purchases")
                        .with(jwt().jwt(token -> token.subject("usuario-invalido")))
                        .header("Idempotency-Key", "purchase-invalid-subject")
                        .contentType("application/json")
                        .content(validBody(1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));

        verifyNoInteractions(purchases);
    }

    @Test
    void jsonMalformadoDevuelve400YSinEjecutarCompra() throws Exception {
        mvc.perform(post("/api/v1/market/purchases")
                        .with(jwtFor(USER_A))
                        .header("Idempotency-Key", "purchase-invalid-json")
                        .contentType("application/json")
                        .content("{\"player_id\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));

        verifyNoInteractions(purchases);
    }

    @ParameterizedTest
    @MethodSource("invalidQuantities")
    void cantidadInvalidaDevuelve400YSinEjecutarCompra(long quantity) throws Exception {
        mvc.perform(post("/api/v1/market/purchases")
                        .with(jwtFor(USER_A))
                        .header("Idempotency-Key", "purchase-invalid-quantity-" + quantity)
                        .contentType("application/json")
                        .content(validBody(quantity)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));

        verifyNoInteractions(purchases);
    }

    private static java.util.stream.Stream<Arguments> invalidQuantities() {
        return java.util.stream.Stream.of(Arguments.of(0L), Arguments.of(-1L), Arguments.of(101L));
    }

    @Test
    void camposObligatoriosAusentesDevuelven400YSinEjecutarCompra() throws Exception {
        mvc.perform(post("/api/v1/market/purchases")
                        .with(jwtFor(USER_A))
                        .header("Idempotency-Key", "purchase-missing-fields")
                        .contentType("application/json")
                        .content("{\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));

        verifyNoInteractions(purchases);
    }

    @ParameterizedTest
    @MethodSource("invalidKeys")
    void claveIdempotenteInvalidaDevuelve400YSinEjecutarCompra(String key) throws Exception {
        var request = post("/api/v1/market/purchases")
                .with(jwtFor(USER_A))
                .contentType("application/json")
                .content(validBody(1));
        if (key != null) {
            request.header("Idempotency-Key", key);
        }

        mvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));

        verifyNoInteractions(purchases);
    }

    private static java.util.stream.Stream<Arguments> invalidKeys() {
        return java.util.stream.Stream.of(Arguments.of((String) null), Arguments.of(" "),
                Arguments.of("x".repeat(201)));
    }

    @ParameterizedTest
    @MethodSource("domainErrors")
    void erroresDeDominioConservanRespuestaEstructurada(TokenMarketException.Reason reason,
            int expectedStatus) throws Exception {
        when(purchases.comprar(any())).thenThrow(new TokenMarketException(reason, "detalle interno"));

        mvc.perform(post("/api/v1/market/purchases")
                        .with(jwtFor(USER_A))
                        .header("Idempotency-Key", "purchase-domain-" + reason)
                        .contentType("application/json")
                        .content(validBody(1)))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not("detalle interno")))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(purchases).comprar(any(MarketPurchaseRequest.class));
    }

    private static java.util.stream.Stream<Arguments> domainErrors() {
        return java.util.stream.Stream.of(
                Arguments.of(TokenMarketException.Reason.QUOTE_UNAVAILABLE, 409),
                Arguments.of(TokenMarketException.Reason.INACTIVE_PLAYER, 409),
                Arguments.of(TokenMarketException.Reason.INSUFFICIENT_BALANCE, 409),
                Arguments.of(TokenMarketException.Reason.INSUFFICIENT_INVENTORY, 409),
                Arguments.of(TokenMarketException.Reason.ACCOUNT_NOT_FOUND, 404),
                Arguments.of(TokenMarketException.Reason.MARKET_NOT_READY, 503));
    }

    @Test
    void identidadDelCompradorProvieneDelJwtAunqueElCuerpoNoLaIncluya() throws Exception {
        when(purchases.comprar(any())).thenReturn(confirmed());

        mvc.perform(post("/api/v1/market/purchases")
                        .with(jwtFor(USER_A))
                        .header("Idempotency-Key", "purchase-owner")
                        .contentType("application/json")
                        .content(validBody(1)))
                .andExpect(status().isOk());

        var request = org.mockito.ArgumentCaptor.forClass(MarketPurchaseRequest.class);
        verify(purchases).comprar(request.capture());
        assertThat(request.getValue().usuarioId()).isEqualTo(USER_A);
        assertThat(request.getValue().usuarioId()).isNotEqualTo(USER_B);
    }

    @Test
    void userIdEnElCuerpoEsUnCampoDesconocidoYSeRechazaSinEjecutarCompra() throws Exception {
        mvc.perform(post("/api/v1/market/purchases")
                        .with(jwtFor(USER_A))
                        .header("Idempotency-Key", "purchase-foreign-user")
                        .contentType("application/json")
                        .content(bodyWithUserId(USER_B)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));

        verifyNoInteractions(purchases);
    }

    private static MarketPurchaseResult confirmed() {
        return new MarketPurchaseResult(OPERATION_ID, TRANSACTION_ID, USER_A, PLAYER_ID, 3L,
                new BigDecimal("1.25"), new BigDecimal("3.75"),
                MarketIdempotencyStatus.CONFIRMED, UPDATED_AT);
    }

    private static String validBody(long quantity) {
        return "{\"player_id\":\"" + PLAYER_ID + "\",\"quantity\":" + quantity
                + ",\"quote_id\":\"" + QUOTE_ID + "\",\"quote_version\":1}";
    }

    private static String bodyWithUserId(UUID userId) {
        return "{\"player_id\":\"" + PLAYER_ID
                + "\",\"quantity\":1,\"quote_id\":\"" + QUOTE_ID
                + "\",\"quote_version\":1,\"user_id\":\"" + userId + "\"}";
    }

    private static RequestPostProcessor jwtFor(UUID userId) {
        return jwt().jwt(token -> token.subject("usr_" + userId));
    }
}
