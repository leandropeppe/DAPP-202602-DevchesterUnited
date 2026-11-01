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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MarketP2POfferQueryController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class})
class MarketP2POfferPlayerNameControllerTest {
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MarketP2POfferQueryService queries;

    @Test
    void aceptaPlayerNameYDevuelvePlayerNameSinPerderPlayerId() throws Exception {
        when(queries.consultar(argThat(query -> "valverde".equals(query.nombreJugador()))))
                .thenReturn(pageResult());

        mvc.perform(get("/api/v1/market/p2p/offers")
                        .with(jwt().jwt(token -> token.subject("usr_" + USER_ID)))
                        .queryParam("playerName", "valverde"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.offers[0].player_id").value(PLAYER_ID.toString()))
                .andExpect(jsonPath("$.data.offers[0].player_name").value("Federico Valverde"));

        verify(queries).consultar(argThat(query -> "valverde".equals(query.nombreJugador())));
    }

    private MarketP2POfferPageResult pageResult() {
        MarketP2POfferView view = new MarketP2POfferView(
                new MarketP2POfferView.Identity(UUID.randomUUID(), PLAYER_ID,
                        "Federico Valverde", "public-alias", false),
                new MarketP2POfferView.Availability(3L, "PURCHASABLE", true),
                new MarketP2POfferView.Quote(UUID.randomUUID(), 2L, new BigDecimal("2.50"),
                        Instant.parse("2026-10-30T11:00:00Z"),
                        Instant.parse("2026-10-30T11:00:00Z"), null));
        return new MarketP2POfferPageResult(List.of(view), new PaginacionResultado(1, 20, 1, 1));
    }
}
