package com.devchesterunited.devchesterunitedv2.e2e.market;

import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketSaleService;
import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
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

import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "token-market.initialization.auto-start=false")
@AutoConfigureMockMvc
class MarketSaleMockMvcTest extends PostgresTestSupport {
    private static final UUID USER_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID QUOTE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MarketSaleService sales;

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
    @MethodSource("invalidQuantities")
    void cantidadInvalidaSigueSiendo400YSinEjecutarVenta(long quantity) throws Exception {
        mvc.perform(post("/api/v1/market/sales")
                        .with(jwtFor(USER_A))
                        .header("Idempotency-Key", "sale-invalid-quantity-" + quantity)
                        .contentType("application/json")
                        .content(validBody(quantity)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));

        verifyNoInteractions(sales);
    }

    private static Stream<Arguments> invalidQuantities() {
        return Stream.of(Arguments.of(0L), Arguments.of(-1L), Arguments.of(101L));
    }

    @Test
    void bodyIncompletoOJsonMalformadoSigueSiendo400YSinEjecutarVenta() throws Exception {
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
    void userIdEnElBodySigueSiendoPropiedadNoPermitida() throws Exception {
        mvc.perform(post("/api/v1/market/sales")
                        .with(jwtFor(USER_A))
                        .header("Idempotency-Key", "sale-foreign-user")
                        .contentType("application/json")
                        .content(bodyWithUserId(USER_B)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));

        verifyNoInteractions(sales);
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
