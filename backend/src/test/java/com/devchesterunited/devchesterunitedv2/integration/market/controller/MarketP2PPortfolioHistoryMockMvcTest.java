package com.devchesterunited.devchesterunitedv2.integration.market.controller;

import com.devchesterunited.devchesterunitedv2.configuration.security.SecurityConfig;
import com.devchesterunited.devchesterunitedv2.configuration.security.SecurityErrorHandler;
import com.devchesterunited.devchesterunitedv2.controller.market.MarketController;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPortfolioResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPortfolioPosition;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketQuoteAvailability;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketTransactionRecord;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketTransactionResult;
import com.devchesterunited.devchesterunitedv2.service.dto.shared.PaginacionResultado;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** T064: HTTP contract for the extended portfolio and unified history. */
@WebMvcTest(controllers = MarketController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class})
class MarketP2PPortfolioHistoryMockMvcTest {
    private static final UUID USER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PLAYER = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Autowired private MockMvc mvc;
    @MockitoBean private MarketBalanceService balances;
    @MockitoBean private MarketAvailabilityService availability;
    @MockitoBean private MarketIdempotencyService idempotency;
    @MockitoBean private MarketPurchaseService purchases;
    @MockitoBean private MarketPortfolioService portfolios;
    @MockitoBean private MarketTransactionService transactions;

    @Test
    void portfolioExponeCantidadesExtendidasYRechazaUsuarioArbitrario() throws Exception {
        MarketPortfolioPosition position = new MarketPortfolioPosition(PLAYER, 10,
                new MarketPortfolioPosition.Costs(new BigDecimal("20.00"), new BigDecimal("2.00")),
                new MarketPortfolioPosition.Valuation(MarketQuoteAvailability.ABSENT,
                        Optional.empty(), Optional.empty(), Optional.empty()));
        when(portfolios.consultar(USER)).thenReturn(new MarketPortfolioResult(List.of(position),
                BigDecimal.ZERO, Optional.of(BigDecimal.ZERO), Optional.of(BigDecimal.ZERO), true));

        mvc.perform(get("/api/v1/market/portfolio").queryParam("userId", PLAYER.toString())
                        .with(jwt().jwt(token -> token.subject("usr_" + USER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.open_positions").isArray())
                .andExpect(jsonPath("$.data.open_positions[0].total_quantity").value(10))
                .andExpect(jsonPath("$.data.open_positions[0].reserved_quantity").value(0))
                .andExpect(jsonPath("$.data.open_positions[0].available_quantity").value(10));

        verify(portfolios).consultar(USER);
    }

    @Test
    void historialAceptaPaginacionTiposP2PYAislaElJwt() throws Exception {
        MarketTransactionRecord record = org.mockito.Mockito.mock(MarketTransactionRecord.class);
        when(record.id()).thenReturn(UUID.randomUUID());
        when(record.confirmadoEn()).thenReturn(Instant.parse("2026-10-30T12:00:00Z"));
        when(record.jugadorId()).thenReturn(PLAYER);
        when(record.nombreJugadorSnapshot()).thenReturn("Player");
        when(record.tipoOperacion()).thenReturn("P2P_PURCHASE");
        when(record.cantidad()).thenReturn(2L);
        when(record.precioUnitario()).thenReturn(new BigDecimal("2.00"));
        when(record.montoTotal()).thenReturn(new BigDecimal("4.00"));
        when(record.contraparteId()).thenReturn(UUID.randomUUID());
        when(record.quoteId()).thenReturn(UUID.randomUUID());
        when(record.quoteVersion()).thenReturn(1L);
        when(record.solicitudIdempotenciaId()).thenReturn(UUID.randomUUID());
        when(transactions.consultar(eq(USER), any())).thenReturn(new MarketTransactionResult(
                List.of(record), new PaginacionResultado(2, 10, 1, 1)));

        mvc.perform(get("/api/v1/market/transactions").queryParam("type", "P2P_PURCHASE")
                        .queryParam("page", "2").queryParam("limit", "10")
                        .with(jwt().jwt(token -> token.subject("usr_" + USER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactions[0].operation_type").value("P2P_PURCHASE"))
                .andExpect(jsonPath("$.data.pagination.current_page").value(2));

        verify(transactions).consultar(eq(USER), any());
    }

    @Test
    void endpointsRequierenAutenticacion() throws Exception {
        mvc.perform(get("/api/v1/market/portfolio")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/market/transactions")).andExpect(status().isUnauthorized());
        verifyNoInteractions(portfolios, transactions);
    }
}
