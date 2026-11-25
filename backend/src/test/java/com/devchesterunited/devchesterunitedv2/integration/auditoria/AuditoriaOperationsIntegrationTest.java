package com.devchesterunited.devchesterunitedv2.integration.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaAspect;
import com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaEventWriter;
import com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaEvento;
import com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaOperationCatalog;
import java.util.ArrayList;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Integration contract for the closed US1 boundary inventory. */
class AuditoriaOperationsIntegrationTest {
    @Test
    void userStoryOneInventoryIsClosedAndComplete() {
        assertThat(AuditoriaOperationCatalog.operations()).hasSize(10);
    }

    @Test
    void everySelectedBoundaryClosesWithControlledErrorAndOriginalException() throws Throwable {
        AuditoriaEventWriter writer = mock(AuditoriaEventWriter.class);
        AuditoriaAspect aspect = new AuditoriaAspect(writer);
        List<Invocation> boundaries = List.of(
                aspect::catalogSync, aspect::statisticsSync, aspect::valuationManual,
                aspect::valuationWeekly, aspect::marketPurchase, aspect::marketSale,
                aspect::marketP2POffer, aspect::marketP2PPurchase, aspect::marketP2PCancel,
                aspect::marketInitialize);
        List<IllegalStateException> originals = new ArrayList<>();
        for (Invocation boundary : boundaries) {
            ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
            IllegalStateException original = new IllegalStateException("secret-message");
            originals.add(original);
            when(joinPoint.proceed()).thenThrow(original);
            try {
                boundary.invoke(joinPoint);
            } catch (IllegalStateException actual) {
                assertThat(actual).isSameAs(original);
            }
        }
        ArgumentCaptor<AuditoriaEvento> events = ArgumentCaptor.forClass(AuditoriaEvento.class);
        org.mockito.Mockito.verify(writer, org.mockito.Mockito.times(20)).write(events.capture());
        List<AuditoriaEvento> captured = events.getAllValues();
        assertThat(captured).filteredOn(event -> event.stage() == AuditoriaEvento.Stage.END)
                .hasSize(10).allSatisfy(event -> {
                    assertThat(event.result()).isEqualTo(AuditoriaEvento.Result.ERROR);
                    assertThat(event.errorCode()).isEqualTo("UNEXPECTED_ERROR");
                    assertThat(event.durationMs()).isGreaterThanOrEqualTo(0L);
                });
        assertThat(captured).noneMatch(event -> "secret-message".equals(event.errorCode()));
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(ProceedingJoinPoint joinPoint) throws Throwable;
    }
}
