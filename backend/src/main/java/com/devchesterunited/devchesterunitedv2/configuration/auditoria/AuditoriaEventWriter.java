package com.devchesterunited.devchesterunitedv2.configuration.auditoria;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** Destino SLF4J con una allowlist explícita de eventos y aislamiento de fallos. */
@Component
public class AuditoriaEventWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditoriaEventWriter.class);
    private static final String MESSAGE = "audit_event";
    private static final String PREFIX = "audit.";

    public void write(AuditoriaEvento event) {
        if (event == null) return;
        Map<String, String> previous = null;
        try {
            Map<String, String> values = fields(event);
            previous = MDC.getCopyOfContextMap();
            // Un evento de log sólo debe contener la allowlist de auditoría, nunca valores
            // arbitrarios del MDC instalados por código HTTP, de seguridad o de negocio.
            MDC.clear();
            values.forEach((key, value) -> {
                if (value != null) MDC.put(key, value);
            });
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info(render(values));
            }
        } catch (RuntimeException ignored) {
            // La auditoría es deliberadamente de mejor esfuerzo; nunca propaga un fallo de logging.
        } finally {
            restore(previous);
        }
    }

    private static void restore(Map<String, String> previous) {
        try {
            MDC.clear();
            if (previous != null) MDC.setContextMap(previous);
        } catch (RuntimeException ignored) {
            // La restauración del MDC forma parte del mecanismo de auditoría y también es de mejor esfuerzo.
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

    private static String render(Map<String, String> fields) {
        StringBuilder message = new StringBuilder(MESSAGE);
        fields.forEach((key, value) -> {
            if (value != null) {
                message.append(' ').append(key.substring(PREFIX.length())).append('=').append(value);
            }
        });
        return message.toString();
    }
}
