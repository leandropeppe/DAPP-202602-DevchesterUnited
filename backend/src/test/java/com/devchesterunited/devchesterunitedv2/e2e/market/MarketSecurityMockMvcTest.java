package com.devchesterunited.devchesterunitedv2.controller;

import static org.mockito.Mockito.never;
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
import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketAvailabilityService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketBalanceService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MarketSecurityMockMvcTest extends PostgresTestSupport {
    private static final UUID USER_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Autowired MockMvc mvc;
    @MockitoBean MarketAvailabilityService availability;
    @MockitoBean MarketBalanceService balances;

    @Test
    void endpointsDelMercadoRechazanAusenciaDeJwt() throws Exception {
        mvc.perform(get("/api/v1/market/balance")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/market/players")).andExpect(status().isUnauthorized());
        verifyNoInteractions(balances, availability);
    }

    @Test
    void jwtInvalidoEsRechazado() throws Exception {
        mvc.perform(get("/api/v1/market/balance").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(balances);
    }

    @Test
    void jwtValidoHabilitaLecturaYElParametroDeOtraCuentaEsIgnorado() throws Exception {
        when(balances.consultar(USER_A)).thenReturn(balance("READY", "100.00"));

        mvc.perform(get("/api/v1/market/balance")
                        .param("userId", USER_B.toString())
                        .param("accountId", UUID.randomUUID().toString())
                        .with(jwt().jwt(token -> token.subject("usr_" + USER_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(100.00));

        verify(balances).consultar(USER_A);
        verify(balances, never()).consultar(USER_B);
    }

    @Test
    void jwtValidoSinCuentaNoRecibeLaCuentaDeOtroUsuario() throws Exception {
        when(balances.consultar(USER_B)).thenReturn(balanceWithoutAccount());

        mvc.perform(get("/api/v1/market/balance")
                        .with(jwt().jwt(token -> token.subject("usr_" + USER_B))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"));

        verify(balances).consultar(USER_B);
        verify(balances, never()).consultar(USER_A);
    }

    @Test
    void listadoAutenticadoUsaElServicioDeDisponibilidad() throws Exception {
        when(availability.consultarJugadores(1, 20)).thenReturn(
                new MarketAvailabilityResult(EstadoMercado.READY, 2L, true,
                        1, 20, 0L, 0L, List.of()));

        mvc.perform(get("/api/v1/market/players")
                        .with(jwt().jwt(token -> token.subject("usr_" + USER_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.players").isArray());

        verify(availability).consultarJugadores(1, 20);
    }

    @Test
    void mercadoNoReadyDevuelve503Estructurado() throws Exception {
        when(availability.consultarJugadores(1, 20)).thenReturn(
                new MarketAvailabilityResult(EstadoMercado.FAILED, 3L, false,
                        1, 20, 0L, 0L, List.of()));

        mvc.perform(get("/api/v1/market/players")
                        .with(jwt().jwt(token -> token.subject("usr_" + USER_A))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("El servicio no está disponible temporalmente."));
    }

    @Test
    void paginacionInvalidaDevuelve400YSinConsultarPersistencia() throws Exception {
        mvc.perform(get("/api/v1/market/players").param("page", "0")
                        .with(jwt().jwt(token -> token.subject("usr_" + USER_A))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));

        verifyNoInteractions(availability);
    }

    @Test
    void razonesDeDominioSeMapeanComoConflictoSinExponerElDetalle() throws Exception {
        when(availability.consultarJugadores(1, 20)).thenThrow(
                new TokenMarketException(TokenMarketException.Reason.QUOTE_UNAVAILABLE,
                        "detalle interno de cotizacion"));

        mvc.perform(get("/api/v1/market/players")
                        .with(jwt().jwt(token -> token.subject("usr_" + USER_A))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("La solicitud entra en conflicto con el estado actual del recurso."));
    }

    @Test
    void rutasInternasSiguenBloqueadasYJugadoresPublicosSiguenDisponibles() throws Exception {
        mvc.perform(get("/api/v1/market/internal")
                        .with(jwt().jwt(token -> token.subject("usr_" + USER_A))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/players")).andExpect(status().isOk());
    }

    @Test
    void documentacionExponeSeguridadYParametrosDeAmbosEndpoints() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/market/balance'].get.security[0].BearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/market/players'].get.parameters[0].name").exists());
    }

    private static MarketBalanceResult balance(String status, String saldo) {
        return new MarketBalanceResult(EstadoMercado.valueOf(status), 1L, "READY".equals(status),
                Optional.of(new BigDecimal(saldo)));
    }

    private static MarketBalanceResult balanceWithoutAccount() {
        return new MarketBalanceResult(EstadoMercado.READY, 1L, true, Optional.empty());
    }
}
