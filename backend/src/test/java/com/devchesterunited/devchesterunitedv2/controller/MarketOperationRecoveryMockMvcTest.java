package com.devchesterunited.devchesterunitedv2.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketIdempotencyResult;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketIdempotencyStatus;
import com.devchesterunited.devchesterunitedv2.service.interfaces.MarketIdempotencyService;
import java.time.Instant;
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
class MarketOperationRecoveryMockMvcTest extends PostgresTestSupport {
    private static final UUID USER_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final Instant UPDATED = Instant.parse("2026-10-15T12:00:00Z");
    private static final String KEY = "purchase-123";

    @Autowired MockMvc mvc;
    @MockitoBean MarketIdempotencyService idempotency;

    @Test
    void rechazaJwtAusenteEInvalido() throws Exception {
        mvc.perform(get("/api/v1/market/operations/" + KEY))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/market/operations/" + KEY)
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(idempotency);
    }

    @Test
    void propietarioConsultaProcessingSinExponerDatosInternos() throws Exception {
        when(idempotency.recuperar(USER_A, KEY)).thenReturn(Optional.of(result(USER_A, KEY,
                MarketIdempotencyStatus.PROCESSING, UUID.randomUUID(), UUID.randomUUID())));

        mvc.perform(get("/api/v1/market/operations/" + KEY)
                        .with(jwt().jwt(token -> token.subject("usr_" + USER_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idempotency_key").value(KEY))
                .andExpect(jsonPath("$.data.operation_type").value("PURCHASE"))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.result_reference").doesNotExist())
                .andExpect(jsonPath("$.data.execution_token").doesNotExist())
                .andExpect(jsonPath("$.data.parameters_hash").doesNotExist())
                .andExpect(jsonPath("$.data.user_id").doesNotExist());
        verify(idempotency).recuperar(USER_A, KEY);
    }

    @Test
    void aislaUsuariosYNoDevuelveLaOperacionDeOtroPropietario() throws Exception {
        when(idempotency.recuperar(USER_B, KEY)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/market/operations/" + KEY)
                        .with(jwt().jwt(token -> token.subject("usr_" + USER_B))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"));
        verify(idempotency).recuperar(USER_B, KEY);
        verifyNoMoreInteractions(idempotency);
    }

    @Test
    void devuelveCadaEstadoYReferenciaSoloCuandoLaOperacionEstaConfirmada() throws Exception {
        for (MarketIdempotencyStatus operationStatus : MarketIdempotencyStatus.values()) {
            UUID resultReference = UUID.randomUUID();
            when(idempotency.recuperar(USER_A, KEY + operationStatus.name()))
                    .thenReturn(Optional.of(result(USER_A, KEY + operationStatus.name(),
                            operationStatus, UUID.randomUUID(), resultReference)));

            var response = mvc.perform(get("/api/v1/market/operations/" + KEY + operationStatus.name())
                            .with(jwt().jwt(token -> token.subject("usr_" + USER_A))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value(operationStatus.name()));
            if (operationStatus == MarketIdempotencyStatus.CONFIRMED) {
                response.andExpect(jsonPath("$.data.result_reference").value(resultReference.toString()));
            } else {
                response.andExpect(jsonPath("$.data.result_reference").doesNotExist());
            }
        }
    }

    @Test
    void claveInexistenteDevuelve404SinRevelarDetalles() throws Exception {
        when(idempotency.recuperar(USER_A, "missing")).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/market/operations/missing")
                        .with(jwt().jwt(token -> token.subject("usr_" + USER_A))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Recurso no encontrado."));
    }

    @Test
    void claveInvalidaNoConsultaElServicio() throws Exception {
        mvc.perform(get("/api/v1/market/operations/" + "x".repeat(201))
                        .with(jwt().jwt(token -> token.subject("usr_" + USER_A))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
        verifyNoInteractions(idempotency);
    }

    @Test
    void documentacionExponeElEndpointYRequiereBearerAuth() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/market/operations/{idempotency-key}'].get.security[0].BearerAuth").isArray());
    }

    private static MarketIdempotencyResult result(UUID user, String key, MarketIdempotencyStatus status,
            UUID executionToken, UUID resultReference) {
        return new MarketIdempotencyResult(UUID.randomUUID(), user, key, "PURCHASE", "sensitive-hash",
                status, executionToken, status == MarketIdempotencyStatus.CONFIRMED ? resultReference : resultReference,
                UPDATED);
    }
}
