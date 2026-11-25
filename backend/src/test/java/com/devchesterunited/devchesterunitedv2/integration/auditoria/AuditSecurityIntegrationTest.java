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

class AuditSecurityIntegrationTest {
    private final List<AuditoriaEvento> events = new ArrayList<>();

    @AfterEach
    void cleanup() { CorrelacionContext.clear(); }

    @Test
    void completeEventsExcludeSensitiveArgumentsReturnsHeadersAndThrowableDetails() throws Throwable {
        AuditoriaEventWriter writer = new AuditoriaEventWriter() {
            @Override public void write(AuditoriaEvento event) { events.add(event); }
        };
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getArgs()).thenReturn(new Object[] {
                "Bearer synthetic.jwt", "api-key=synthetic", "password=synthetic", "header-secret" });
        when(joinPoint.proceed()).thenReturn("personal-data-return-value");
        CorrelacionContext.installHttp("client-safe-label");

        Object result = new AuditoriaAspect(writer).marketInitialize(joinPoint);

        assertThat(result).isEqualTo("personal-data-return-value");
        assertThat(events).extracting(AuditoriaEvento::stage)
                .containsExactly(AuditoriaEvento.Stage.START, AuditoriaEvento.Stage.END);
        assertThat(events).allSatisfy(event -> {
            assertThat(event.requestCorrelationId()).isNotBlank();
            assertThat(event.externalCorrelationId()).isEqualTo("client-safe-label");
            assertThat(event.executionCorrelationId()).isNull();
            assertThat(event.errorCode()).isNull();
        });
        assertThat(events.toString()).doesNotContain("synthetic.jwt", "api-key", "password",
                "header-secret", "personal-data-return-value");
    }
}
