package com.devchesterunited.devchesterunitedv2.unit.market;


import com.devchesterunited.devchesterunitedv2.contract.market.MarketIdempotencyServiceContractTest;
import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.SolicitudIdempotenciaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.SolicitudIdempotenciaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyRequest;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketIdempotencyServiceImpl;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketOperationExecutionLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class MarketIdempotencyServiceImplTest extends MarketIdempotencyServiceContractTest {
    private SolicitudIdempotenciaMercadoRepository solicitudes;

    @Mock
    private MarketOperationExecutionLock executionLock;

    private final Map<UUID, SolicitudIdempotenciaMercadoEntity> rows = new HashMap<>();

    @BeforeEach
    void setUpRepositoryDouble() {
        rows.clear();
        solicitudes = mock(SolicitudIdempotenciaMercadoRepository.class,
                this::answerRepositoryCall);
    }

    @Override
    protected com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketIdempotencyService
            service(Clock requestedClock, Duration processingTimeout) {
        when(executionLock.tryAcquire(any(), any())).thenReturn(true);
        return new MarketIdempotencyServiceImpl(solicitudes, requestedClock, processingTimeout,
                executionLock);
    }

    @org.junit.jupiter.api.Test
    void asegurarPropiedadUsaLaAdquisicionBloqueanteYValidaElToken() {
        when(executionLock.tryAcquire(any(), any())).thenReturn(true);
        var service = new MarketIdempotencyServiceImpl(solicitudes,
                Clock.fixed(START, java.time.ZoneOffset.UTC), Duration.ofMinutes(5), executionLock);
        var claim = service.reclamar(requestFor(1L));

        assertThat(claim.solicitudId()).isNotNull();
        assertThat(claim.tokenEjecucion()).isNotNull();
        service.asegurarPropiedadDeEjecucion(claim.solicitudId(), claim.tokenEjecucion());

        verify(executionLock).acquire(USER_ID, "market-key");
    }

    @org.junit.jupiter.api.Test
    void asegurarPropiedadRechazaUnTokenReemplazado() {
        when(executionLock.tryAcquire(any(), any())).thenReturn(true);
        var clock = new TestClock(START);
        var service = new MarketIdempotencyServiceImpl(solicitudes, clock,
                Duration.ofMinutes(5), executionLock);
        var original = service.reclamar(requestFor(1L));
        assertThat(original.solicitudId()).isNotNull();
        assertThat(original.tokenEjecucion()).isNotNull();
        clock.advance(Duration.ofMinutes(6));
        var recovered = service.reclamar(requestFor(1L));

        assertThat(recovered.solicitudId()).isNotNull();
        assertThat(recovered.tokenEjecucion()).isNotNull();
        assertThat(recovered.tokenEjecucion()).isNotEqualTo(original.tokenEjecucion());
        assertThatThrownBy(() -> service.asegurarPropiedadDeEjecucion(
                original.solicitudId(), original.tokenEjecucion()))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.reason())
                                .isEqualTo(TokenMarketException.Reason.STALE_REQUEST));
    }

    private static MarketIdempotencyRequest requestFor(long quantity) {
        return new MarketIdempotencyRequest(USER_ID, "market-key", "PURCHASE", PLAYER_ID,
                quantity, QUOTE_ID, 1L);
    }

    private static final class TestClock extends Clock {
        private Instant current;

        private TestClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }

    private Optional<SolicitudIdempotenciaMercadoEntity> findByUserAndKey(
            UUID userId, String key) {
        return rows.values().stream()
                .filter(row -> row.getUsuarioId().equals(userId))
                .filter(row -> row.getClaveIdempotencia().equals(key))
                .findFirst();
    }

    private Object answerRepositoryCall(InvocationOnMock invocation) throws Throwable {
        return switch (invocation.getMethod().getName()) {
            case "findByUsuarioIdAndClaveForUpdate", "findByUsuarioIdAndClaveIdempotencia" ->
                    findByUserAndKey(invocation.getArgument(0), invocation.getArgument(1));
            case "findById", "findByIdForUpdate" ->
                    Optional.ofNullable(rows.get(invocation.getArgument(0)));
            case "saveAndFlush" -> {
                SolicitudIdempotenciaMercadoEntity entity = invocation.getArgument(0);
                rows.put(entity.getId(), entity);
                yield entity;
            }
            default -> Answers.RETURNS_DEFAULTS.answer(invocation);
        };
    }
}
