package com.devchesterunited.devchesterunitedv2.integration.market.controller;

import com.devchesterunited.devchesterunitedv2.configuration.security.SecurityConfig;
import com.devchesterunited.devchesterunitedv2.configuration.security.SecurityErrorHandler;
import com.devchesterunited.devchesterunitedv2.controller.market.MarketP2POfferQueryController;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2POfferPageResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2POfferView;
import com.devchesterunited.devchesterunitedv2.service.dto.shared.PaginacionResultado;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2POfferQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MarketP2POfferQueryController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class})
class MarketP2POfferListMockMvcTest {
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OFFER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MarketP2POfferQueryService queries;

    @Test
    void listaOfertasConFiltrosPaginacionYAliasPublico() throws Exception {
        when(queries.consultar(any())).thenReturn(pageResult());

        mvc.perform(get("/api/v1/market/p2p/offers")
                        .with(jwt().jwt(token -> token.subject("usr_" + USER_ID)))
                        .queryParam("playerId", PLAYER_ID.toString())
                        .queryParam("status", "PURCHASABLE")
                        .queryParam("mine", "true")
                        .queryParam("page", "2")
                        .queryParam("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.offers[0].offer_id").value(OFFER_ID.toString()))
                .andExpect(jsonPath("$.data.offers[0].seller_alias").value("public-alias"))
                .andExpect(jsonPath("$.data.offers[0].purchasable").value(true))
                .andExpect(jsonPath("$.data.pagination.current_page").value(2))
                .andExpect(jsonPath("$.data.pagination.page_size").value(5));

        verify(queries).consultar(any());
    }

    @Test
    void requiereJwt() throws Exception {
        mvc.perform(get("/api/v1/market/p2p/offers"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(queries);
    }

    @Test
    void subjectInvalidoNoPermiteConsultar() throws Exception {
        mvc.perform(get("/api/v1/market/p2p/offers")
                        .with(jwt().jwt(token -> token.subject("invalid-subject"))))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(queries);
    }

    @Test
    void playerIdInvalidoNoInvocaServicio() throws Exception {
        mvc.perform(get("/api/v1/market/p2p/offers")
                        .with(jwt().jwt(token -> token.subject("usr_" + USER_ID)))
                        .queryParam("playerId", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(queries);
    }

    private MarketP2POfferPageResult pageResult() {
        MarketP2POfferView view = new MarketP2POfferView(
                new MarketP2POfferView.Identity(OFFER_ID, PLAYER_ID, "public-alias"),
                new MarketP2POfferView.Availability(3L, "PURCHASABLE", true),
                new MarketP2POfferView.Quote(UUID.randomUUID(), 2L, new BigDecimal("2.50"),
                        Instant.parse("2026-10-30T11:00:00Z"),
                        Instant.parse("2026-10-30T11:00:00Z"), null));
        return new MarketP2POfferPageResult(List.of(view), new PaginacionResultado(2, 5, 3, 11));
    }
}
