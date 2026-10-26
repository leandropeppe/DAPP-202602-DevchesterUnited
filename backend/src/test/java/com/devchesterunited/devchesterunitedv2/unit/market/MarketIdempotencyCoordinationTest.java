package com.devchesterunited.devchesterunitedv2.unit.market;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.persistence.market.repository.SolicitudIdempotenciaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyStatus;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketIdempotencyServiceImpl;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketOperationExecutionLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class MarketIdempotencyCoordinationTest {
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PLAYER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID QUOTE_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Mock
    private SolicitudIdempotenciaMercadoRepository solicitudes;

    @Mock
    private MarketOperationExecutionLock executionLock;

    @Test
    void lockOcupadoDevuelveProcessingSinCrearUnaSegundaSolicitud() {
        var request = new MarketIdempotencyRequest(USER_ID, "market-key", "PURCHASE",
                PLAYER_ID, 1L, QUOTE_ID, 1L);
        when(executionLock.tryAcquire(USER_ID, "market-key")).thenReturn(false);
        when(solicitudes.findByUsuarioIdAndClaveIdempotencia(USER_ID, "market-key"))
                .thenReturn(Optional.empty());

        var service = new MarketIdempotencyServiceImpl(solicitudes,
                Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC),
                Duration.ofMinutes(5), executionLock);

        var result = service.reclamar(request);

        assertThat(result.estado()).isEqualTo(MarketIdempotencyStatus.PROCESSING);
        assertThat(result.solicitudId()).isNull();
        verify(solicitudes, never()).findByUsuarioIdAndClaveForUpdate(any(), any());
        verify(solicitudes, never()).saveAndFlush(any());
    }
}
