package com.devchesterunited.devchesterunitedv2.configuration.auditoria;

import java.util.Optional;
import java.util.UUID;

/** Estado de correlación asociado a la solicitud, no persistente y retenido sólo en el hilo actual. */
public final class CorrelacionContext {
    private static final ThreadLocal<CorrelacionContext> CURRENT = new ThreadLocal<>();
    private static final int MAX_EXTERNAL_LENGTH = 128;

    private final AuditoriaEvento.ContextType contextType;
    private final String requestCorrelationId;
    private final String executionCorrelationId;
    private final String externalCorrelationId;

    private CorrelacionContext(AuditoriaEvento.ContextType contextType, String requestCorrelationId,
            String executionCorrelationId, String externalCorrelationId) {
        this.contextType = contextType;
        this.requestCorrelationId = requestCorrelationId;
        this.executionCorrelationId = executionCorrelationId;
        this.externalCorrelationId = externalCorrelationId;
    }

    public static CorrelacionContext installHttp(String externalCorrelationId) {
        CorrelacionContext context = new CorrelacionContext(AuditoriaEvento.ContextType.HTTP,
                UUID.randomUUID().toString(), null, normalizeExternalCorrelationId(externalCorrelationId));
        CURRENT.set(context);
        return context;
    }

    public static CorrelacionContext installBackground() {
        CorrelacionContext context = new CorrelacionContext(AuditoriaEvento.ContextType.BACKGROUND,
                null, UUID.randomUUID().toString(), null);
        CURRENT.set(context);
        return context;
    }

    public static Optional<CorrelacionContext> current() { return Optional.ofNullable(CURRENT.get()); }
    public static void clear() { CURRENT.remove(); }
    public AuditoriaEvento.ContextType contextType() { return contextType; }
    public String requestCorrelationId() { return requestCorrelationId; }
    public String executionCorrelationId() { return executionCorrelationId; }
    public String externalCorrelationId() { return externalCorrelationId; }
    public static boolean isValidExternalCorrelationId(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_EXTERNAL_LENGTH) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            boolean alphanumeric = character >= 'A' && character <= 'Z'
                    || character >= 'a' && character <= 'z'
                    || character >= '0' && character <= '9';
            if (!alphanumeric && character != '.' && character != '_' && character != '-') {
                return false;
            }
        }
        return true;
    }
    public static String normalizeExternalCorrelationId(String value) { return isValidExternalCorrelationId(value) ? value : null; }
}
