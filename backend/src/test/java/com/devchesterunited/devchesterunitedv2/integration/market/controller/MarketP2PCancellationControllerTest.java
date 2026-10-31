package com.devchesterunited.devchesterunitedv2.integration.market.controller;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PCancelCommand;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PCancelResult;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2PCancellationService;
import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "token-market.initialization.auto-start=false")
@AutoConfigureMockMvc
class MarketP2PCancellationControllerTest extends PostgresTestSupport {
    private static final UUID SELLER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_USER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID OFFER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OTHER_OFFER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MarketP2PCancellationService cancellations;

    @Test
    void cancelaOfertaOpenYDevuelveAuditoriaEnEnvelopeSuccess() throws Exception {
        when(cancellations.cancelar(any())).thenReturn(result(4L, 0L, 4L));

        mvc.perform(post(path()).with(jwtFor(SELLER_ID)).header("Idempotency-Key", " open-key "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.offer_id").value(OFFER_ID.toString()))
                .andExpect(jsonPath("$.data.original_quantity").value(4))
                .andExpect(jsonPath("$.data.sold_quantity").value(0))
                .andExpect(jsonPath("$.data.released_quantity").value(4))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void cancelaOfertaParcialYConservaCantidadVendida() throws Exception {
        when(cancellations.cancelar(any())).thenReturn(result(4L, 1L, 3L));

        mvc.perform(post(path()).with(jwtFor(SELLER_ID)).header("Idempotency-Key", "partial-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.original_quantity").value(4))
                .andExpect(jsonPath("$.data.sold_quantity").value(1))
                .andExpect(jsonPath("$.data.released_quantity").value(3))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void derivaVendedorDelJwtYPropagaOfferIdYKeyNormalizada() throws Exception {
        when(cancellations.cancelar(any())).thenReturn(result(4L, 0L, 4L));

        mvc.perform(post(path()).with(jwtFor(SELLER_ID))
                        .header("Idempotency-Key", "  normalized-key  "))
                .andExpect(status().isOk());

        var command = org.mockito.ArgumentCaptor.forClass(MarketP2PCancelCommand.class);
        verify(cancellations).cancelar(command.capture());
        assertThat(command.getValue().usuarioId()).isEqualTo(SELLER_ID);
        assertThat(command.getValue().offerId()).isEqualTo(OFFER_ID);
        assertThat(command.getValue().claveIdempotencia()).isEqualTo("normalized-key");
    }

    @Test
    void replayConLaMismaKeyConservaElResultadoYElEnvelope() throws Exception {
        when(cancellations.cancelar(any())).thenReturn(result(4L, 0L, 4L));

        mvc.perform(post(path()).with(jwtFor(SELLER_ID)).header("Idempotency-Key", "same-key"))
                .andExpect(status().isOk());
        mvc.perform(post(path()).with(jwtFor(SELLER_ID)).header("Idempotency-Key", "same-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.released_quantity").value(4));

        verify(cancellations, times(2)).cancelar(any());
    }

    @Test
    void conflictoPorReutilizarKeySeExponeComo409() throws Exception {
        doAnswer(invocation -> {
            MarketP2PCancelCommand command = invocation.getArgument(0);
            if (OTHER_OFFER_ID.equals(command.offerId())) {
                throw new TokenMarketException(TokenMarketException.Reason.IDEMPOTENCY_CONFLICT,
                        "conflict");
            }
            return result(4L, 0L, 4L);
        }).when(cancellations).cancelar(any());

        mvc.perform(post(path()).with(jwtFor(SELLER_ID)).header("Idempotency-Key", "reused"))
                .andExpect(status().isOk());
        mvc.perform(post(path(OTHER_OFFER_ID)).with(jwtFor(SELLER_ID))
                        .header("Idempotency-Key", "reused"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void otraKeySobreOfertaCanceladaEsConflicto() throws Exception {
        doThrow(new TokenMarketException(TokenMarketException.Reason.OFFER_ALREADY_CANCELLED,
                "cancelled")).when(cancellations).cancelar(any());

        mvc.perform(post(path()).with(jwtFor(SELLER_ID)).header("Idempotency-Key", "new-key"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OFFER_ALREADY_CANCELLED"));
    }

    @Test
    void usuarioNoPropietarioRecibe404SinCambiarLaIdentidad() throws Exception {
        doThrow(new TokenMarketException(TokenMarketException.Reason.OFFER_NOT_FOUND,
                "not-owner")).when(cancellations).cancelar(any());

        mvc.perform(post(path()).with(jwtFor(OTHER_USER_ID)).header("Idempotency-Key", "other"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OFFER_NOT_FOUND"));

        var command = org.mockito.ArgumentCaptor.forClass(MarketP2PCancelCommand.class);
        verify(cancellations).cancelar(command.capture());
        assertThat(command.getValue().usuarioId()).isEqualTo(OTHER_USER_ID);
    }

    @Test
    void ofertaFilledEsConflictoDeNegocio() throws Exception {
        doThrow(new TokenMarketException(TokenMarketException.Reason.OFFER_NOT_PURCHASABLE,
                "filled")).when(cancellations).cancelar(any());

        mvc.perform(post(path()).with(jwtFor(SELLER_ID)).header("Idempotency-Key", "filled"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OFFER_NOT_PURCHASABLE"));
    }

    @Test
    void jwtAusenteOInvalidoNoInvocaElServicio() throws Exception {
        mvc.perform(post(path()).header("Idempotency-Key", "no-jwt"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(path()).with(jwt().jwt(token -> token.subject("invalid")))
                        .header("Idempotency-Key", "bad-jwt"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(cancellations);
    }

    @Test
    void keyAusenteVaciaExcesivaUuidInvalidoNoInvocanElServicio() throws Exception {
        mvc.perform(post(path()).with(jwtFor(SELLER_ID)))
                .andExpect(status().isBadRequest());
        mvc.perform(post(path()).with(jwtFor(SELLER_ID)).header("Idempotency-Key", "   "))
                .andExpect(status().isBadRequest());
        mvc.perform(post(path()).with(jwtFor(SELLER_ID))
                        .header("Idempotency-Key", "a".repeat(201)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/market/p2p/offers/not-a-uuid/cancel")
                        .with(jwtFor(SELLER_ID)).header("Idempotency-Key", "bad-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cancellations);
    }

    private static MarketP2PCancelResult result(long original, long sold, long released) {
        return new MarketP2PCancelResult(OFFER_ID, original, sold, released, "CANCELLED");
    }

    private static String path() {
        return path(OFFER_ID);
    }

    private static String path(UUID offerId) {
        return "/api/v1/market/p2p/offers/" + offerId + "/cancel";
    }

    private static RequestPostProcessor jwtFor(UUID userId) {
        return jwt().jwt(token -> token.subject("usr_" + userId));
    }
}
