package com.devchesterunited.devchesterunitedv2.integration.market.controller;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2POfferResult;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2POfferService;
import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "token-market.initialization.auto-start=false")
@AutoConfigureMockMvc
class MarketP2POfferControllerTest extends PostgresTestSupport {
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OFFER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MarketP2POfferService offers;

    @Test
    void publicaOfertaConIdentidadDelJwtYDevuelveDesgloseDeReserva() throws Exception {
        when(offers.publicar(any())).thenReturn(new MarketP2POfferResult(OFFER_ID, USER_ID,
                PLAYER_ID, new MarketP2POfferResult.Quantities(3L, 3L, 3L), "OPEN"));

        mvc.perform(post("/api/v1/market/p2p/offers")
                        .with(jwtFor(USER_ID))
                        .header("Idempotency-Key", "controller-key")
                        .contentType("application/json")
                        .content(body(3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.offer_id").value(OFFER_ID.toString()))
                .andExpect(jsonPath("$.data.player_id").value(PLAYER_ID.toString()))
                .andExpect(jsonPath("$.data.original_quantity").value(3))
                .andExpect(jsonPath("$.data.remaining_quantity").value(3))
                .andExpect(jsonPath("$.data.reserved_quantity").value(3))
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        verify(offers).publicar(any());
    }

    @Test
    void jwtAusenteDevuelve401YSinPublicar() throws Exception {
        mvc.perform(post("/api/v1/market/p2p/offers")
                        .header("Idempotency-Key", "no-auth")
                        .contentType("application/json")
                        .content(body(1)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(offers);
    }

    @Test
    void jwtConSubjectMalformadoDevuelve401YSinPublicar() throws Exception {
        mvc.perform(post("/api/v1/market/p2p/offers")
                        .with(jwt().jwt(token -> token.subject("invalid-subject")))
                        .header("Idempotency-Key", "malformed-subject")
                        .contentType("application/json")
                        .content(body(1)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(offers);
    }

    @Test
    void precioManualDevuelve400YSinPublicar() throws Exception {
        mvc.perform(post("/api/v1/market/p2p/offers")
                        .with(jwtFor(USER_ID))
                        .header("Idempotency-Key", "manual-price")
                        .contentType("application/json")
                        .content("{\"player_id\":\"" + PLAYER_ID
                                + "\",\"quantity\":1,\"price\":1.25}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(offers);
    }

    @Test
    void cantidadInvalidaDevuelve400YSinPublicar() throws Exception {
        mvc.perform(post("/api/v1/market/p2p/offers")
                        .with(jwtFor(USER_ID))
                        .header("Idempotency-Key", "invalid-quantity")
                        .contentType("application/json")
                        .content(body(0)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(offers);
    }

    @Test
    void userIdEnBodyDevuelve400YSinPublicar() throws Exception {
        mvc.perform(post("/api/v1/market/p2p/offers")
                        .with(jwtFor(USER_ID))
                        .header("Idempotency-Key", "body-user-id")
                        .contentType("application/json")
                        .content("{\"player_id\":\"" + PLAYER_ID
                                + "\",\"quantity\":1,\"user_id\":\""
                                + UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb") + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));

        verifyNoInteractions(offers);
    }

    private static String body(long quantity) {
        return "{\"player_id\":\"" + PLAYER_ID + "\",\"quantity\":" + quantity + "}";
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(
            UUID userId) {
        return jwt().jwt(token -> token.subject("usr_" + userId));
    }
}
