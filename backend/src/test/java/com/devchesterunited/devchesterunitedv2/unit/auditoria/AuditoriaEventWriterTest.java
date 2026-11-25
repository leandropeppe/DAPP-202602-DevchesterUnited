package com.devchesterunited.devchesterunitedv2.unit.auditoria;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.time.Instant;
import com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaErrorCodeResolver;
import com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaEventWriter;
import com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaEvento;
import com.devchesterunited.devchesterunitedv2.configuration.auditoria.CorrelacionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class AuditoriaEventWriterTest {
    @AfterEach void cleanup() { MDC.clear(); CorrelacionContext.clear(); }

    @Test void writesOnlyAllowlistedContextAndRestoresMdc() {
        CorrelacionContext context = CorrelacionContext.installHttp("external.ok");
        AuditoriaEvento event = AuditoriaEvento.end("market.sale", AuditoriaEvento.Result.ERROR, 4,
                Instant.now(), context, AuditoriaErrorCodeResolver.UNEXPECTED_ERROR);
        MDC.put("audit.operation", "preexisting");
        assertDoesNotThrow(() -> new AuditoriaEventWriter().write(event));
        assertEquals("preexisting", MDC.get("audit.operation"));
        org.junit.jupiter.api.Assertions.assertNull(MDC.get("audit.errorMessage"));
    }
}
