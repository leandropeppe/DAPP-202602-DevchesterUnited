package com.devchesterunited.devchesterunitedv2.unit.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaAspect;
import com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaEventWriter;
import com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaEvento;
import java.util.ArrayList;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class AuditoriaBestEffortTest {
    @AfterEach
    void cleanup() { MDC.clear(); }

    @Test
    void writerRuntimeFailureDoesNotChangeSuccessfulReturn() throws Throwable {
        AuditoriaEventWriter writer = new AuditoriaEventWriter() {
            @Override public void write(com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaEvento event) {
                throw new RuntimeException("logging sink failed");
            }
        };
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Object result = new Object();
        when(joinPoint.proceed()).thenReturn(result);

        Object actual = new AuditoriaAspect(writer).marketInitialize(joinPoint);

        assertThat(actual).isSameAs(result);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void writerRuntimeFailureDoesNotChangeOriginalBusinessException() throws Throwable {
        AuditoriaEventWriter writer = new AuditoriaEventWriter() {
            @Override public void write(com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaEvento event) {
                throw new RuntimeException("logging sink failed");
            }
        };
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        IllegalStateException original = new IllegalStateException("sensitive business message");
        when(joinPoint.proceed()).thenThrow(original);

        Throwable actual = null;
        try {
            new AuditoriaAspect(writer).marketInitialize(joinPoint);
        } catch (Throwable error) {
            actual = error;
        }

        assertThat(actual).isSameAs(original);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void schedulerAndStartupBoundariesCreateBackgroundCorrelationOnly() throws Throwable {
        List<AuditoriaEvento> events = new ArrayList<>();
        AuditoriaEventWriter writer = new AuditoriaEventWriter() {
            @Override public void write(AuditoriaEvento event) { events.add(event); }
        };
        AuditoriaAspect aspect = new AuditoriaAspect(writer);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn(null);

        aspect.statisticsSync(joinPoint);
        aspect.valuationWeekly(joinPoint);
        aspect.marketInitialize(joinPoint);

        assertThat(events).hasSize(6).allSatisfy(event -> {
            assertThat(event.contextType()).isEqualTo(AuditoriaEvento.ContextType.BACKGROUND);
            assertThat(event.executionCorrelationId()).isNotBlank();
            assertThat(event.requestCorrelationId()).isNull();
            assertThat(event.externalCorrelationId()).isNull();
        });
        assertThat(events).extracting(AuditoriaEvento::operation)
                .containsExactly("statistics.sync", "statistics.sync", "valuation.weekly",
                        "valuation.weekly", "market.initialize", "market.initialize");
    }
}
