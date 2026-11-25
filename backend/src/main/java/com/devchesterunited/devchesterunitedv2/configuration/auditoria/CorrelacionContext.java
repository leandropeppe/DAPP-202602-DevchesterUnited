package com.devchesterunited.devchesterunitedv2.configuration.auditoria;

import java.util.Optional;
import java.util.UUID;

/** Request-scoped, non-persistent correlation state held only for the current thread. */
public final class CorrelacionContext {
    private static final ThreadLocal<CorrelacionContext> CURRENT = new ThreadLocal<>();
    private static final String EXTERNAL_PATTERN = "[A-Za-z0-9._-]{1,128}";

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
    public static boolean isValidExternalCorrelationId(String value) { return value != null && value.matches(EXTERNAL_PATTERN); }
    public static String normalizeExternalCorrelationId(String value) { return isValidExternalCorrelationId(value) ? value : null; }
}
