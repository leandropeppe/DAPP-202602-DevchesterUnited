package com.devchesterunited.devchesterunitedv2.configuration.auditoria;

import java.time.Instant;
import java.util.Objects;

/** Immutable, ephemeral audit record. It intentionally contains no business data. */
public record AuditoriaEvento(
        String eventName,
        String operation,
        Stage stage,
        Result result,
        Long durationMs,
        Instant timestamp,
        String requestCorrelationId,
        String executionCorrelationId,
        String externalCorrelationId,
        ContextType contextType,
        String errorCode) {

    public static final String EVENT_NAME = "AUDIT_OPERATION";

    public enum Stage { START, END }
    public enum Result { STARTED, SUCCESS, ERROR }
    public enum ContextType { HTTP, BACKGROUND }

    public AuditoriaEvento {
        if (!EVENT_NAME.equals(eventName)) {
            throw new IllegalArgumentException("eventName must be AUDIT_OPERATION");
        }
        if (!AuditoriaOperationCatalog.isSupported(operation)) {
            throw new IllegalArgumentException("operation is not in the closed audit catalog");
        }
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(contextType, "contextType");
        if (stage == Stage.START && (result != Result.STARTED || durationMs != null || errorCode != null)) {
            throw new IllegalArgumentException("START must be STARTED and have no duration or error code");
        }
        if (stage == Stage.END && (durationMs == null || durationMs < 0
                || (result == Result.ERROR) != (errorCode != null && !errorCode.isBlank())
                || result == Result.STARTED)) {
            throw new IllegalArgumentException("END has an invalid result, duration or error code");
        }
        if (contextType == ContextType.HTTP) {
            if (isBlank(requestCorrelationId) || executionCorrelationId != null) {
                throw new IllegalArgumentException("HTTP requires only requestCorrelationId");
            }
            if (externalCorrelationId != null && !CorrelacionContext.isValidExternalCorrelationId(externalCorrelationId)) {
                throw new IllegalArgumentException("externalCorrelationId is invalid");
            }
        } else if (isBlank(executionCorrelationId) || requestCorrelationId != null || externalCorrelationId != null) {
            throw new IllegalArgumentException("BACKGROUND requires only executionCorrelationId");
        }
        if (errorCode != null && !AuditoriaErrorCodeResolver.isControlledCode(errorCode)) {
            throw new IllegalArgumentException("errorCode is not controlled");
        }
    }

    public static AuditoriaEvento start(String operation, Instant timestamp, CorrelacionContext context) {
        return new AuditoriaEvento(EVENT_NAME, operation, Stage.START, Result.STARTED, null, timestamp,
                context.requestCorrelationId(), context.executionCorrelationId(), context.externalCorrelationId(),
                context.contextType(), null);
    }

    public static AuditoriaEvento end(String operation, Result result, long durationMs, Instant timestamp,
            CorrelacionContext context, String errorCode) {
        return new AuditoriaEvento(EVENT_NAME, operation, Stage.END, result, durationMs, timestamp,
                context.requestCorrelationId(), context.executionCorrelationId(), context.externalCorrelationId(),
                context.contextType(), errorCode);
    }

    private static boolean isBlank(String value) { return value == null || value.isBlank(); }
}
