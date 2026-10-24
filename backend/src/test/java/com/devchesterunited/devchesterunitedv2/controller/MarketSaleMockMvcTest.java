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
import com.devchesterunited.devchesterunitedv2.service.dto.MarketIdempotencyStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketSaleRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketSaleResult;
import com.devchesterunited.devchesterunitedv2.service.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.interfaces.MarketSaleService;
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
class MarketSaleMockMvcTest extends PostgresTestSupport {
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
    private MarketSaleService sales;

    @Test
    void ventaParcialConfirmadaDevuelveRespuestaPublicaYCantidadSolicitada() throws Exception {
        when(sales.vender(any())).thenReturn(confirmed(2L, new BigDecimal("2.50"),
                new BigDecimal("5.00")));

        mvc.perform(post("/api/v1/market/sales")
                        .with(jwtFor(USER_A))
                        .header("Idempotency-Key", " sale-partial ")
                        .contentType("application/json")
                        .content(validBody(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.operation_id").value(OPERATION_ID.toString()))
                .andExpect(jsonPath("$.data.transaction_id").value(TRANSACTION_ID.toString()))
                .andExpect(jsonPath("$.data.player_id").value(PLAYER_ID.toString()))
                .andExpect(jsonPath("$.data.quantity").value(2))
                .andExpect(jsonPath("$.data.unit_price").value(2.50))
                .andExpect(jsonPath("$.data.total_amount").value(5.00))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.updated_at").value(UPDATED_AT.toString()));

        var request = org.mockito.ArgumentCaptor.forClass(MarketSaleRequest.class);
        verify(sales).vender(request.capture());
        assertThat(request.getValue().usuarioId()).isEqualTo(USER_A);
        assertThat(request.getValue().claveIdempotencia()).isEqualTo("sale-partial");
        assertThat(request.getValue().cantidad()).isEqualTo(2L);
    }

    @Test
    void ventaFinalConfirmadaNoExponeCostosInternos() throws Exception {
        when(sales.vender(any())).thenReturn(confirmed(3L, new BigDecimal("1.25"),
                new BigDecimal("3.75")));

        mvc.perform(post("/api/v1/market/sales")
                        .with(jwtFor(USER_A))
                        .header("Idempotency-Key", "sale-final")
                        .contentType("application/json")
                        .content(validBody(3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.quantity").value(3))
                .andExpect(jsonPath("$.data.transaction_id").value(TRANSACTION_ID.toString()))
                .andExpect(jsonPath("$.data.acquisition_cost").doesNotExist())
                .andExpect(jsonPath("$.data.realized_pnl").doesNotExist())
                .andExpect(jsonPath("$.data.user_id").doesNotExist())
                .andExpect(jsonPath("$.data.execution_token").doesNotExist())
                .andExpect(jsonPath("$.data.parameters_hash").doesNotExist());
    }

    @Test
    void ventaProcessingDevuelve200SinImportesNiTransaccionInexistentes() throws Exception {
        when(sales.vender(any())).thenReturn(new MarketSaleResult(
                OPERATION_ID, null, USER_A, PLAYER_ID, null, null, null,
                MarketIdempotencyStatus.PROCESSING, UPDATED_AT));

        mvc.perform(post("/api/v1/market/sales")
                        .with(jwtFor(USER_A))
                        .header("Idempotency-Key", "sale-processing")
                        .contentType("application/json")
                        .content(validBody(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.operation_id").value(OPERATION_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.transaction_id").doesNotExist())
                .andExpect(jsonPath("$.data.unit_price").doesNotExist())
                .andExpect(jsonPath("$.data.total_amount").doesNotExist())
                .andExpect(jsonPath("$.data.user_id").doesNotExist())
                .andExpect(jsonPath("$.data.execution_token").doesNotExist())
                .andExpect(jsonPath("$.data.parameters_hash").doesNotExist());

        verify(sales).vender(any(MarketSaleRequest.class));
    }

    @ParameterizedTest
    @MethodSource("domainErrors")
    void erroresDeVentaDevuelven409EstructuradoSinDetalleInterno(
            TokenMarketException.Reason reason) throws Exception {
        when(sales.vender(any())).thenThrow(new TokenMarketException(reason, "detalle interno"));

        mvc.perform(post("/api/v1/market/sales")
                        .with(jwtFor(USER_A))
                        .header("Idempotency-Key", "sale-domain-" + reason)
                        .contentType("application/json")
                        .content(validBody(1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not("detalle interno")))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(sales).vender(any(MarketSaleRequest.class));
    }

    private static java.util.stream.Stream<Arguments> domainErrors() {
        return java.util.stream.Stream.of(
                Arguments.of(TokenMarketException.Reason.INSUFFICIENT_INVENTORY),
                Arguments.of(TokenMarketException.Reason.INSUFFICIENT_BALANCE),
                Arguments.of(TokenMarketException.Reason.INACTIVE_PLAYER),
                Arguments.of(TokenMarketException.Reason.QUOTE_UNAVAILABLE),
                Arguments.of(TokenMarketException.Reason.IDEMPOTENCY_CONFLICT),
                Arguments.of(TokenMarketException.Reason.STALE_REQUEST));
    }

    @Test
    void reintentoIdempotenteConservaOperacionTransaccionYClaveNormalizada() throws Exception {
        when(sales.vender(any())).thenReturn(confirmed(1L, new BigDecimal("2.00"),
                new BigDecimal("2.00")));

        mvc.perform(post("/api/v1/market/sales")
                        .with(jwtFor(USER_A))
                        .header("Idempotency-Key", " sale-retry ")
                        .contentType("application/json")
                        .content(validBody(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operation_id").value(OPERATION_ID.toString()))
                .andExpect(jsonPath("$.data.transaction_id").value(TRANSACTION_ID.toString()));

        mvc.perform(post("/api/v1/market/sales")
                        .with(jwtFor(USER_A))
                        .header("Idempotency-Key", " sale-retry ")
                        .contentType("application/json")
                        .content(validBody(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operation_id").value(OPERATION_ID.toString()))
                .andExpect(jsonPath("$.data.transaction_id").value(TRANSACTION_ID.toString()));

        var requests = org.mockito.ArgumentCaptor.forClass(MarketSaleRequest.class);
        verify(sales, org.mockito.Mockito.times(2)).vender(requests.capture());
        assertThat(requests.getAllValues()).allMatch(request -> USER_A.equals(request.usuarioId())
                && "sale-retry".equals(request.claveIdempotencia()));
    }

    @Test
    void jwtAusenteDevuelve401YSinEjecutarVenta() throws Exception {
        mvc.perform(post("/api/v1/market/sales")
                        .header("Idempotency-Key", "sale-no-auth")
                        .contentType("application/json")
                        .content(validBody(1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));

        verifyNoInteractions(sales);
    }

    @Test
    void bearerInvalidoDevuelve401YSinEjecutarVenta() throws Exception {
        mvc.perform(post("/api/v1/market/sales")
                        .header("Authorization", "Bearer invalid-token")
                        .header("Idempotency-Key", "sale-invalid-token")
                        .contentType("application/json")
                        .content(validBody(1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));

        verifyNoInteractions(sales);
    }

    @Test
    void subjectJwtMalFormadoDevuelve401YSinEjecutarVenta() throws Exception {
        mvc.perform(post("/api/v1/market/sales")
                        .with(jwt().jwt(token -> token.subject("usuario-invalido")))
                        .header("Idempotency-Key", "sale-invalid-subject")
                        .contentType("application/json")
                        .content(validBody(1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"));

        verifyNoInteractions(sales);
    }

    @ParameterizedTest
    @MethodSource("invalidKeys")
    void claveIdempotenteInvalidaDevuelve400YSinEjecutarVenta(String key) throws Exception {
        var request = post("/api/v1/market/sales")
                .with(jwtFor(USER_A))
                .contentType("application/json")
                .content(validBody(1));
        if (key != null) {
            request.header("Idempotency-Key", key);
        }

        mvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));

        verifyNoInteractions(sales);
    }

    private static java.util.stream.Stream<Arguments> invalidKeys() {
        return java.util.stream.Stream.of(Arguments.of((String) null), Arguments.of(" "),
                Arguments.of("x".repeat(201)));
    }

    @ParameterizedTest
    @MethodSource("invalidQuantities")
    void cantidadInvalidaDevuelve400YSinEjecutarVenta(long quantity) throws Exception {
        mvc.perform(post("/api/v1/market/sales")
                        .with(jwtFor(USER_A))
                        .header("Idempotency-Key", "sale-invalid-quantity-" + quantity)
                        .contentType("application/json")
                        .content(validBody(quantity)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));

        verifyNoInteractions(sales);
    }

    private static java.util.stream.Stream<Arguments> invalidQuantities() {
        return java.util.stream.Stream.of(Arguments.of(0L), Arguments.of(-1L), Arguments.of(101L));
    }

    @Test
    void bodyIncompletoOJsonMalformadoDevuelve400YSinEjecutarVenta() throws Exception {
        mvc.perform(post("/api/v1/market/sales")
                        .with(jwtFor(USER_A))
                        .header("Idempotency-Key", "sale-missing-fields")
                        .contentType("application/json")
                        .content("{\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));

        mvc.perform(post("/api/v1/market/sales")
                        .with(jwtFor(USER_A))
                        .header("Idempotency-Key", "sale-invalid-json")
                        .contentType("application/json")
                        .content("{\"player_id\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));

        verifyNoInteractions(sales);
    }

    @Test
    void userIdEnElBodyEsPropiedadNoPermitidaYNoEjecutaVenta() throws Exception {
        mvc.perform(post("/api/v1/market/sales")
                        .with(jwtFor(USER_A))
                        .header("Idempotency-Key", "sale-foreign-user")
                        .contentType("application/json")
                        .content(bodyWithUserId(USER_B)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));

        verifyNoInteractions(sales);
    }

    @Test
    void pertenenciaDeVentaProvieneExclusivamenteDelJwt() throws Exception {
        when(sales.vender(any())).thenReturn(confirmed(1L, new BigDecimal("1.25"),
                new BigDecimal("1.25")));

        mvc.perform(post("/api/v1/market/sales")
                        .with(jwtFor(USER_A))
                        .header("Idempotency-Key", "sale-owner")
                        .contentType("application/json")
                        .content(validBody(1)))
                .andExpect(status().isOk());

        var request = org.mockito.ArgumentCaptor.forClass(MarketSaleRequest.class);
        verify(sales).vender(request.capture());
        assertThat(request.getValue().usuarioId()).isEqualTo(USER_A);
        assertThat(request.getValue().usuarioId()).isNotEqualTo(USER_B);
    }

    private static MarketSaleResult confirmed(long quantity, BigDecimal unitPrice,
            BigDecimal totalAmount) {
        return new MarketSaleResult(OPERATION_ID, TRANSACTION_ID, USER_A, PLAYER_ID, quantity,
                unitPrice, totalAmount, MarketIdempotencyStatus.CONFIRMED, UPDATED_AT);
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
