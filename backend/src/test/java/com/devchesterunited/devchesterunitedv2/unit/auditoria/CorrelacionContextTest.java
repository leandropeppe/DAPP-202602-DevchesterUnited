package com.devchesterunited.devchesterunitedv2.unit.auditoria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaEvento;
import com.devchesterunited.devchesterunitedv2.configuration.auditoria.CorrelacionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CorrelacionContextTest {
    @AfterEach void cleanup() { CorrelacionContext.clear(); }

    @Test void installsAndReadsHttpContextWithoutUsingExternalIdAsIdentity() {
        CorrelacionContext context = CorrelacionContext.installHttp("external-id");
        assertEquals(AuditoriaEvento.ContextType.HTTP, CorrelacionContext.current().orElseThrow().contextType());
        assertEquals(context.requestCorrelationId(), CorrelacionContext.current().orElseThrow().requestCorrelationId());
        assertEquals("external-id", context.externalCorrelationId());
        assertTrue(!context.requestCorrelationId().equals(context.externalCorrelationId()));
    }

    @Test void ignoresInvalidExternalValuesAndSupportsBackground() {
        CorrelacionContext http = CorrelacionContext.installHttp("bad value");
        assertNull(http.externalCorrelationId());
        CorrelacionContext background = CorrelacionContext.installBackground();
        assertEquals(AuditoriaEvento.ContextType.BACKGROUND, background.contextType());
        assertNull(background.requestCorrelationId());
        assertNull(background.externalCorrelationId());
    }

    @Test void clearsContext() {
        CorrelacionContext.installHttp(null);
        CorrelacionContext.clear();
        assertTrue(CorrelacionContext.current().isEmpty());
    }
}
