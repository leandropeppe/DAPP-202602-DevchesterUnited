package com.devchesterunited.devchesterunitedv2.configuration.auditoria;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** SLF4J sink with an explicit event allowlist and failure isolation. */
@Component
public class AuditoriaEventWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditoriaEventWriter.class);
    private static final String MESSAGE = "audit_event";
    private static final String PREFIX = "audit.";

    public void write(AuditoriaEvento event) {
        if (event == null) return;
        Map<String, String> values = fields(event);
        Map<String, String> previous = new LinkedHashMap<>();
        try {
            values.forEach((key, value) -> {
                previous.put(key, MDC.get(key));
                if (value == null) MDC.remove(key); else MDC.put(key, value);
            });
            LOGGER.info(MESSAGE);
        } catch (RuntimeException ignored) {
            // Audit is deliberately best effort; never propagate a logging failure.
        } finally {
            values.keySet().forEach(key -> {
                String old = previous.get(key);
                if (old == null) MDC.remove(key); else MDC.put(key, old);
            });
        }
    }

    private Map<String, String> fields(AuditoriaEvento event) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(PREFIX + "eventName", event.eventName());
        fields.put(PREFIX + "operation", event.operation());
        fields.put(PREFIX + "stage", event.stage().name());
        fields.put(PREFIX + "result", event.result().name());
        fields.put(PREFIX + "durationMs", event.durationMs() == null ? null : event.durationMs().toString());
        fields.put(PREFIX + "timestamp", event.timestamp().toString());
        fields.put(PREFIX + "contextType", event.contextType().name());
        fields.put(PREFIX + "requestCorrelationId", event.requestCorrelationId());
        fields.put(PREFIX + "executionCorrelationId", event.executionCorrelationId());
        fields.put(PREFIX + "externalCorrelationId", event.externalCorrelationId());
        fields.put(PREFIX + "errorCode", event.errorCode());
        return fields;
    }
}
