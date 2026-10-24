package com.devchesterunited.devchesterunitedv2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketRejectedAttempt;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketRejectionFinalizationResult;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketIdempotencyRejectionFinalizationPort;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketRejectionAuditTransactionalWriter;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketRejectionFinalizationServiceImpl;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketRejectionFinalizationServiceTest {
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final UUID TOKEN = UUID.randomUUID();
    private static final MarketRejectedAttempt ATTEMPT = new MarketRejectedAttempt(
            UUID.randomUUID(), "PURCHASE", null, null, "INVALID_INPUT", null,
            Instant.parse("2026-10-19T12:00:00Z"));

    @Mock
    private MarketIdempotencyRejectionFinalizationPort idempotency;
    @Mock
    private MarketRejectionAuditTransactionalWriter auditWriter;

    private MarketRejectionFinalizationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MarketRejectionFinalizationServiceImpl(idempotency, auditWriter);
    }

    @Test
    void transicionEfectivaEscribeUnaAuditoria() {
        when(idempotency.rechazarParaFinalizacion(REQUEST_ID, TOKEN))
                .thenReturn(result(MarketIdempotencyStatus.REJECTED, true));

        MarketRejectionFinalizationResult result =
                service.finalizarRechazo(REQUEST_ID, TOKEN, ATTEMPT);

        assertThat(result.transicionRealizada()).isTrue();
        verify(auditWriter).registrarEnTransaccionActual(ATTEMPT);
    }

    @Test
    void noOpNoEscribeAuditoria() {
        when(idempotency.rechazarParaFinalizacion(REQUEST_ID, TOKEN))
                .thenReturn(result(MarketIdempotencyStatus.CONFIRMED, false));

        MarketRejectionFinalizationResult result =
                service.finalizarRechazo(REQUEST_ID, TOKEN, ATTEMPT);

        assertThat(result.transicionRealizada()).isFalse();
        verifyNoInteractions(auditWriter);
    }

    private static MarketRejectionFinalizationResult result(
            MarketIdempotencyStatus status, boolean transitioned) {
        return new MarketRejectionFinalizationResult(new MarketIdempotencyResult(
                REQUEST_ID, ATTEMPT.usuarioId(), "key", "PURCHASE", null, status,
                transitioned ? null : TOKEN, null, ATTEMPT.creadoEn()), transitioned);
    }
}
