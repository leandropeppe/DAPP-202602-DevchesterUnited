package com.devchesterunited.devchesterunitedv2.unit.market;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyClaim;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketRejectedAttempt;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketSaleRequest;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.TransaccionConfirmadaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketSaleServiceImpl;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketIdempotencyService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketRejectionFinalizationService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketSaleExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class MarketSaleRejectionServiceTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final UUID QUOTE_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final UUID TOKEN = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");

    @Mock private MarketIdempotencyService idempotency;
    @Mock private MarketSaleExecutionService execution;
    @Mock private MarketRejectionFinalizationService finalization;
    @Mock private TransaccionConfirmadaMercadoRepository transactions;

    private MarketSaleServiceImpl service;
    private MarketSaleRequest request;
    private MarketIdempotencyClaim claim;

    @BeforeEach
    void setUp() {
        service = new MarketSaleServiceImpl(idempotency, execution, finalization,
                transactions, Clock.fixed(NOW, ZoneOffset.UTC));
        request = new MarketSaleRequest(USER_ID, "sale-key", PLAYER_ID, 2L, QUOTE_ID, 1L);
        MarketIdempotencyResult result = new MarketIdempotencyResult(REQUEST_ID, USER_ID,
                "sale-key", "SALE", "a".repeat(64), MarketIdempotencyStatus.PROCESSING,
                TOKEN, null, NOW);
        claim = new MarketIdempotencyClaim(result, true);
        when(idempotency.reclamarConPropiedad(any(MarketIdempotencyRequest.class)))
                .thenReturn(claim);
    }

    @Test
    void rechazoDeTenenciaFinalizaYRelanzaLaMismaExcepcion() {
        TokenMarketException failure = new TokenMarketException(
                TokenMarketException.Reason.INSUFFICIENT_INVENTORY, "holding");
        when(execution.ejecutar(request, claim)).thenThrow(failure);

        assertThatThrownBy(() -> service.vender(request)).isSameAs(failure);

        verify(finalization).finalizarRechazo(any(), any(), any(MarketRejectedAttempt.class));
    }

    @Test
    void errorTecnicoStaleNoSeConvierteEnRechazoAuditado() {
        TokenMarketException failure = new TokenMarketException(
                TokenMarketException.Reason.STALE_REQUEST, "stale");
        when(execution.ejecutar(request, claim)).thenThrow(failure);

        assertThatThrownBy(() -> service.vender(request)).isSameAs(failure);

        verify(finalization, never()).finalizarRechazo(any(), any(), any());
    }
}
