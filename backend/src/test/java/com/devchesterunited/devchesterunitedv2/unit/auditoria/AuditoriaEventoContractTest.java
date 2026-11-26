package com.devchesterunited.devchesterunitedv2.unit.auditoria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.time.Instant;
import com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaEvento;
import com.devchesterunited.devchesterunitedv2.configuration.auditoria.CorrelacionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AuditoriaEventoContractTest {
    @AfterEach void cleanup() { CorrelacionContext.clear(); }

    @Test void startAndEndRespectRequiredFields() {
        CorrelacionContext context = CorrelacionContext.installHttp("client.trace_01");
        AuditoriaEvento start = AuditoriaEvento.start("catalog.sync", Instant.now(), context);
        AuditoriaEvento end = AuditoriaEvento.end("catalog.sync", AuditoriaEvento.Result.SUCCESS, 0,
                Instant.now(), context, null);
        assertEquals(AuditoriaEvento.Result.STARTED, start.result());
        assertEquals(null, start.durationMs());
        assertEquals(0L, end.durationMs());
        assertEquals("client.trace_01", end.externalCorrelationId());
    }

    @Test void rejectsInvalidShape() {
        CorrelacionContext context = CorrelacionContext.installBackground();
        assertThrows(IllegalArgumentException.class, () -> new AuditoriaEvento(
                AuditoriaEvento.EVENT_NAME, "unknown", AuditoriaEvento.Stage.START,
                AuditoriaEvento.Result.STARTED, null, Instant.now(), null,
                context.executionCorrelationId(), null, context.contextType(), null));
    }
}
