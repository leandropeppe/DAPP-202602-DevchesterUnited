package com.devchesterunited.devchesterunitedv2.unit.market;

import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketIdempotencyService;

import com.devchesterunited.devchesterunitedv2.contract.market.MarketIdempotencyServiceContractTest;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.SolicitudIdempotenciaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.SolicitudIdempotenciaMercadoRepository;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
