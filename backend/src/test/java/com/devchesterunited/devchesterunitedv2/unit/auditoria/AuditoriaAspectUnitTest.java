package com.devchesterunited.devchesterunitedv2.unit.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaAspect;
import com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaEventWriter;
import com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaEvento;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditoriaAspectUnitTest {
    private final AuditoriaEventWriter writer = mock(AuditoriaEventWriter.class);
    private final AuditoriaAspect aspect = new AuditoriaAspect(writer,
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    @AfterEach
    void cleanContext() {
        com.devchesterunited.devchesterunitedv2.configuration.auditoria.CorrelacionContext.clear();
    }

    @Test
    void emitsStartBeforeProceedAndEndOnSuccess() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenAnswer(invocation -> "result");

        assertThat(aspect.marketInitialize(joinPoint)).isEqualTo("result");

        ArgumentCaptor<AuditoriaEvento> events = ArgumentCaptor.forClass(AuditoriaEvento.class);
        verify(writer, org.mockito.Mockito.times(2)).write(events.capture());
        assertThat(events.getAllValues()).extracting(AuditoriaEvento::stage)
                .containsExactly(AuditoriaEvento.Stage.START, AuditoriaEvento.Stage.END);
        assertThat(events.getAllValues().get(1).durationMs()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void emitsEndOnErrorAndPreservesTheSameException() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        IllegalStateException failure = new IllegalStateException("must not be logged");
        when(joinPoint.proceed()).thenThrow(failure);

        assertThatThrownBy(() -> aspect.catalogSync(joinPoint)).isSameAs(failure);

        ArgumentCaptor<AuditoriaEvento> events = ArgumentCaptor.forClass(AuditoriaEvento.class);
        verify(writer, org.mockito.Mockito.times(2)).write(events.capture());
        AuditoriaEvento end = events.getAllValues().get(1);
        assertThat(end.result()).isEqualTo(AuditoriaEvento.Result.ERROR);
        assertThat(end.errorCode()).isEqualTo("UNEXPECTED_ERROR");
        assertThat(end.durationMs()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void writerFailureDoesNotChangeBusinessResult() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn(42);
        org.mockito.Mockito.doThrow(new IllegalStateException("writer failure"))
                .when(writer).write(org.mockito.ArgumentMatchers.any());

        assertThat(aspect.marketSale(joinPoint)).isEqualTo(42);
    }
}
