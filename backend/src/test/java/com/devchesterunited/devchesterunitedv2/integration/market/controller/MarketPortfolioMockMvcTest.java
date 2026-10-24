package com.devchesterunited.devchesterunitedv2.controller;
import com.devchesterunited.devchesterunitedv2.controller.market.MarketController;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devchesterunited.devchesterunitedv2.configuration.security.SecurityConfig;
import com.devchesterunited.devchesterunitedv2.configuration.security.SecurityErrorHandler;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPortfolioPosition;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPortfolioQuote;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPortfolioResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketQuoteAvailability;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketAvailabilityService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketBalanceService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketIdempotencyService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketPortfolioService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketPurchaseService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketSaleService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketTransactionService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MarketController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class})
class MarketPortfolioMockMvcTest {
    private static final UUID USER_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID PLAYER_A = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID PLAYER_B = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID PLAYER_C = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final UUID QUOTE_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID QUOTE_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-10-15T12:00:00Z");
    private static final Instant VALID_FROM = Instant.parse("2026-10-15T11:00:00Z");
    private static final Instant VALID_UNTIL = Instant.parse("2026-10-15T13:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean private MarketBalanceService balances;
    @MockitoBean private MarketAvailabilityService availability;
    @MockitoBean private MarketIdempotencyService idempotency;
    @MockitoBean private MarketPurchaseService purchases;
    @MockitoBean private MarketSaleService sales;
    @MockitoBean private MarketPortfolioService portfolios;
    @MockitoBean private MarketTransactionService transactions;

    @Test
    void usuarioAutenticadoConsultaSuPortfolioConPosicionYEnvoltorioSuccess() throws Exception {
        MarketPortfolioPosition position = currentPosition(PLAYER_A, QUOTE_A, 3L,
                "3.00000000", "1.00000000", "6.00000000", "3.00000000");
        when(portfolios.consultar(USER_A)).thenReturn(result(List.of(position),
                "1.00000000", Optional.of(new BigDecimal("3.00000000")),
                Optional.of(new BigDecimal("4.00000000")), true));

        mvc.perform(get("/api/v1/market/portfolio").with(jwtFor(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.open_positions[0].player_id").value(PLAYER_A.toString()))
                .andExpect(jsonPath("$.data.open_positions[0].quantity").value(3))
                .andExpect(jsonPath("$.data.open_positions[0].quote_availability").value("CURRENT"))
                .andExpect(jsonPath("$.data.open_positions[0].current_value").value(6.0))
                .andExpect(jsonPath("$.data.realized_pnl").value(1.0))
                .andExpect(jsonPath("$.data.unrealized_pnl").value(3.0))
                .andExpect(jsonPath("$.data.combined_result").value(4.0))
                .andExpect(jsonPath("$.data.combined_result_complete").value(true))
                .andExpect(jsonPath("$.data.open_players").value(1))
                .andExpect(jsonPath("$.data.total_tokens").value(3))
                .andExpect(jsonPath("$.data.total_pending_acquisition_cost").value(3.0))
                .andExpect(jsonPath("$.data.total_current_value").value(6.0));

        verify(portfolios).consultar(USER_A);
    }

    @Test
    void portfolioVacioConservaLaPnlRealizada() throws Exception {
        when(portfolios.consultar(USER_A)).thenReturn(result(List.of(), "2.50000000",
                Optional.of(BigDecimal.ZERO.setScale(8)), Optional.of(new BigDecimal("2.50000000")), true));

        mvc.perform(get("/api/v1/market/portfolio").with(jwtFor(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.open_positions").isEmpty())
                .andExpect(jsonPath("$.data.realized_pnl").value(2.5))
                .andExpect(jsonPath("$.data.unrealized_pnl").value(0.0))
                .andExpect(jsonPath("$.data.combined_result").value(2.5))
                .andExpect(jsonPath("$.data.combined_result_complete").value(true))
                .andExpect(jsonPath("$.data.open_players").value(0))
                .andExpect(jsonPath("$.data.total_tokens").value(0))
                .andExpect(jsonPath("$.data.total_pending_acquisition_cost").value(0.0))
                .andExpect(jsonPath("$.data.total_current_value").value(0.0));
    }

    @Test
    void exponeCotizacionesCurrentHistoricalYAbsentSinValorarLasNoCurrent() throws Exception {
        MarketPortfolioPosition current = currentPosition(PLAYER_A, QUOTE_A, 2L,
                "2.00000000", "1.00000000", "4.00000000", "2.00000000");
        MarketPortfolioPosition historical = new MarketPortfolioPosition(PLAYER_B, 1L,
                new BigDecimal("1.00000000"), new BigDecimal("1.00000000"),
                MarketQuoteAvailability.HISTORICAL,
                Optional.of(quote(QUOTE_B, "3.00000000")), Optional.empty(), Optional.empty());
        MarketPortfolioPosition absent = new MarketPortfolioPosition(PLAYER_C, 4L,
                new BigDecimal("4.00000000"), new BigDecimal("1.00000000"),
                MarketQuoteAvailability.ABSENT, Optional.empty(), Optional.empty(), Optional.empty());
        when(portfolios.consultar(USER_A)).thenReturn(result(List.of(current, historical, absent),
                "0.00000000", Optional.empty(), Optional.empty(), false));

        mvc.perform(get("/api/v1/market/portfolio").with(jwtFor(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.open_positions[0].quote_availability").value("CURRENT"))
                .andExpect(jsonPath("$.data.open_positions[0].current_value").value(4.0))
                .andExpect(jsonPath("$.data.open_positions[1].quote_availability").value("HISTORICAL"))
                .andExpect(jsonPath("$.data.open_positions[1].quote_id").value(QUOTE_B.toString()))
                .andExpect(jsonPath("$.data.open_positions[1].current_value").doesNotExist())
                .andExpect(jsonPath("$.data.open_positions[1].unrealized_pnl").doesNotExist())
                .andExpect(jsonPath("$.data.open_positions[2].quote_availability").value("ABSENT"))
                .andExpect(jsonPath("$.data.open_positions[2].quote_id").doesNotExist())
                .andExpect(jsonPath("$.data.open_positions[2].unit_price").doesNotExist())
                .andExpect(jsonPath("$.data.unrealized_pnl").doesNotExist())
                .andExpect(jsonPath("$.data.combined_result").doesNotExist())
                .andExpect(jsonPath("$.data.combined_result_complete").value(false))
                .andExpect(jsonPath("$.data.open_players").value(3))
                .andExpect(jsonPath("$.data.total_tokens").value(7))
                .andExpect(jsonPath("$.data.total_pending_acquisition_cost").value(7.0))
                .andExpect(jsonPath("$.data.total_current_value").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void rechazaConsultaSinAutenticacionSinInvocarElServicio() throws Exception {
        mvc.perform(get("/api/v1/market/portfolio"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(portfolios);
    }

    @Test
    void rechazaJwtInvalidoSinInvocarElServicio() throws Exception {
        mvc.perform(get("/api/v1/market/portfolio")
                        .header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(portfolios);
    }

    @Test
    void rechazaSubjectMalFormadoSinInvocarElServicio() throws Exception {
        mvc.perform(get("/api/v1/market/portfolio")
                        .with(jwt().jwt(token -> token.subject("usuario-invalido"))))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(portfolios);
    }

    @Test
    void ignoraUnUserIdArbitrarioYUsaElUsuarioDelJwt() throws Exception {
        when(portfolios.consultar(USER_A)).thenReturn(result(List.of(), "0.00000000",
                Optional.of(BigDecimal.ZERO.setScale(8)), Optional.of(BigDecimal.ZERO.setScale(8)), true));

        mvc.perform(get("/api/v1/market/portfolio")
                        .queryParam("userId", USER_B.toString())
                        .with(jwtFor(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.open_positions").isEmpty());

        verify(portfolios).consultar(USER_A);
    }

    @Test
    void aislaLasConsultasDeDosUsuariosAutenticados() throws Exception {
        when(portfolios.consultar(USER_A)).thenReturn(result(List.of(), "1.00000000",
                Optional.of(BigDecimal.ZERO.setScale(8)), Optional.of(new BigDecimal("1.00000000")), true));
        when(portfolios.consultar(USER_B)).thenReturn(result(List.of(), "2.00000000",
                Optional.of(BigDecimal.ZERO.setScale(8)), Optional.of(new BigDecimal("2.00000000")), true));

        mvc.perform(get("/api/v1/market/portfolio").with(jwtFor(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.realized_pnl").value(1.0));
        mvc.perform(get("/api/v1/market/portfolio").with(jwtFor(USER_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.realized_pnl").value(2.0));

        verify(portfolios).consultar(USER_A);
        verify(portfolios).consultar(USER_B);
    }

    private static MarketPortfolioResult result(List<MarketPortfolioPosition> positions,
            String realizedPnl, Optional<BigDecimal> unrealizedPnl,
            Optional<BigDecimal> combinedResult, boolean complete) {
        return new MarketPortfolioResult(positions, new BigDecimal(realizedPnl),
                unrealizedPnl, combinedResult, complete);
    }

    private static MarketPortfolioPosition currentPosition(UUID playerId, UUID quoteId, long quantity,
            String pendingCost, String weightedAverageCost, String currentValue, String unrealizedPnl) {
        return new MarketPortfolioPosition(playerId, quantity, new BigDecimal(pendingCost),
                new BigDecimal(weightedAverageCost), MarketQuoteAvailability.CURRENT,
                Optional.of(quote(quoteId, "2.00000000")), Optional.of(new BigDecimal(currentValue)),
                Optional.of(new BigDecimal(unrealizedPnl)));
    }

    private static MarketPortfolioQuote quote(UUID quoteId, String unitPrice) {
        return new MarketPortfolioQuote(quoteId, 1L, new BigDecimal(unitPrice),
                PUBLISHED_AT, VALID_FROM, VALID_UNTIL);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(UUID userId) {
        return jwt().jwt(token -> token.subject("usr_" + userId));
    }
}
