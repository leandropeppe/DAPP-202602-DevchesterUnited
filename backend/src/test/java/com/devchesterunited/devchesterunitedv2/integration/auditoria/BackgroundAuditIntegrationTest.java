package com.devchesterunited.devchesterunitedv2.integration.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaAspect;
import com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaEventWriter;
import com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaEvento;
import com.devchesterunited.devchesterunitedv2.configuration.auditoria.CorrelacionContext;
import java.util.ArrayList;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BackgroundAuditIntegrationTest {
    private final List<AuditoriaEvento> events = new ArrayList<>();

    @AfterEach
    void cleanup() { CorrelacionContext.clear(); }

    @Test
    void schedulersAndStartupUseIndependentBackgroundCorrelation() throws Throwable {
        AuditoriaAspect aspect = new AuditoriaAspect(new AuditoriaEventWriter() {
            @Override public void write(AuditoriaEvento event) { events.add(event); }
        });
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        when(point.proceed()).thenReturn(null);

        aspect.statisticsSync(point);
        String statisticsExecution = events.get(0).executionCorrelationId();
        aspect.valuationWeekly(point);
        String valuationExecution = events.get(2).executionCorrelationId();
        aspect.marketInitialize(point);
        String startupExecution = events.get(4).executionCorrelationId();

        assertThat(events).hasSize(6).allSatisfy(event -> {
            assertThat(event.contextType()).isEqualTo(AuditoriaEvento.ContextType.BACKGROUND);
            assertThat(event.executionCorrelationId()).isNotBlank();
            assertThat(event.requestCorrelationId()).isNull();
            assertThat(event.externalCorrelationId()).isNull();
        });
        assertThat(List.of(statisticsExecution, valuationExecution, startupExecution))
                .doesNotHaveDuplicates();
    }

    @Test
    void originalBackgroundFailureIsPropagatedWithoutHttpIdentity() throws Throwable {
        AuditoriaEventWriter writer = new AuditoriaEventWriter() {
            @Override public void write(AuditoriaEvento event) { events.add(event); }
        };
        AuditoriaAspect aspect = new AuditoriaAspect(writer);
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        IllegalStateException original = new IllegalStateException("synthetic failure");
        when(point.proceed()).thenThrow(original);

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> aspect.marketInitialize(point));

        assertThat(thrown).isSameAs(original);
        assertThat(events).hasSize(2).allSatisfy(event -> {
            assertThat(event.contextType()).isEqualTo(AuditoriaEvento.ContextType.BACKGROUND);
            assertThat(event.requestCorrelationId()).isNull();
            assertThat(event.executionCorrelationId()).isNotBlank();
        });
        assertThat(events.get(1).result()).isEqualTo(AuditoriaEvento.Result.ERROR);
    }
}
