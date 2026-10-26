package com.devchesterunited.devchesterunitedv2.controller;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.modelo.market.EstadoMercado;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketAvailabilityResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketBalanceResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPlayerAvailability;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketQuoteAvailability;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketAvailabilityService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketBalanceService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MarketControllerE2ETest extends PostgresTestSupport {
    private static final UUID USER_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    @Autowired MockMvc mvc;
    @MockitoBean MarketAvailabilityService availability;
    @MockitoBean MarketBalanceService balances;

    @AfterEach
    void resetearMocks() {
        reset(availability, balances);
    }

    @Test
    void balanceUsaExclusivamenteLaIdentidadDelJwtYAislaLasCuentas() throws Exception {
        when(balances.consultar(USER_A)).thenReturn(balance("READY", "250.00"));

        mvc.perform(get("/api/v1/market/balance")
                        .param("userId", USER_B.toString())
                        .with(jwt().jwt(token -> token.subject("usr_" + USER_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(250.00));

        verify(balances).consultar(USER_A);
    }

    @Test
    void balanceRechazaMercadoNoReadySinModificarElSaldo() throws Exception {
        when(balances.consultar(USER_A)).thenReturn(balance("FAILED", "250.00"));

        mvc.perform(get("/api/v1/market/balance")
                        .with(jwt().jwt(token -> token.subject("usr_" + USER_A))))
                .andExpect(status().isServiceUnavailable());

        verify(balances).consultar(USER_A);
    }

    @Test
    void balanceSinAutenticacionEsRechazado() throws Exception {
        mvc.perform(get("/api/v1/market/balance"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(balances);
    }

    @Test
    void playersExponePaginacionYSeparaCotizacionActualHistoricaYAusente() throws Exception {
        var jugadores = List.of(
                player("11111111-1111-1111-1111-111111111111", MarketQuoteAvailability.CURRENT, true),
                player("22222222-2222-2222-2222-222222222222", MarketQuoteAvailability.HISTORICAL, false),
                player("33333333-3333-3333-3333-333333333333", MarketQuoteAvailability.ABSENT, false));
        when(availability.consultarJugadores(2, 2)).thenReturn(
                new MarketAvailabilityResult(EstadoMercado.READY, 12L, true,
                        2, 2, 5L, 3L, jugadores));

        mvc.perform(get("/api/v1/market/players").param("page", "2").param("limit", "2")
                        .with(jwt().jwt(token -> token.subject("usr_" + USER_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.players[0].quote_availability").value("CURRENT"))
                .andExpect(jsonPath("$.data.players[0].operable").value(true))
                .andExpect(jsonPath("$.data.players[1].quote_availability").value("HISTORICAL"))
                .andExpect(jsonPath("$.data.players[1].tokens_available").value(0))
                .andExpect(jsonPath("$.data.players[2].quote_availability").value("ABSENT"))
                .andExpect(jsonPath("$.data.pagination.current_page").value(2))
                .andExpect(jsonPath("$.data.pagination.page_size").value(2))
                .andExpect(jsonPath("$.data.pagination.total_records").value(5));

        verify(availability).consultarJugadores(2, 2);
    }

    @Test
    void playersNoOperaCuandoMercadoNoReady() throws Exception {
        when(availability.consultarJugadores(1, 20)).thenReturn(
                new MarketAvailabilityResult(EstadoMercado.INITIALIZING, 2L, false,
                        1, 20, 0L, 0L, List.of()));

        mvc.perform(get("/api/v1/market/players")
                        .with(jwt().jwt(token -> token.subject("usr_" + USER_A))))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void playerConsultaDisponibilidadPorId() throws Exception {
        UUID playerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(availability.consultarJugador(playerId)).thenReturn(Optional.of(
                player(playerId.toString(), MarketQuoteAvailability.CURRENT, true)));

        mvc.perform(get("/api/v1/market/players/{playerId}", playerId)
                        .with(jwt().jwt(token -> token.subject("usr_" + USER_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("ply_" + playerId))
                .andExpect(jsonPath("$.data.operable").value(true));

        verify(availability).consultarJugador(playerId);
    }

    private static MarketBalanceResult balance(String status, String saldo) {
        return new MarketBalanceResult(EstadoMercado.valueOf(status), 3L, "READY".equals(status),
                Optional.of(new BigDecimal(saldo)));
    }

    private static MarketPlayerAvailability player(String id, MarketQuoteAvailability quote,
            boolean operable) {
        UUID playerId = UUID.fromString(id);
        boolean hasQuote = quote != MarketQuoteAvailability.ABSENT;
        return new MarketPlayerAvailability(playerId, "Player " + id.substring(0, 4), "League", "Team",
                true, true, 100L, operable ? 50L : 0L,
                hasQuote ? UUID.randomUUID() : null, hasQuote ? 1L : null,
                hasQuote ? new BigDecimal("1.00") : null, hasQuote ? "VALID" : null,
                hasQuote ? NOW : null, null, quote, operable);
    }
}
