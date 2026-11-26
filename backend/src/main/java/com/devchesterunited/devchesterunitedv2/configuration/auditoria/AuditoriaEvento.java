package com.devchesterunited.devchesterunitedv2.configuration.auditoria;

import java.time.Instant;
import java.util.Objects;

/** Registro de auditoría inmutable y efímero. Intencionalmente no contiene datos de negocio. */
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
        validateIdentity(eventName, operation);
        validateRequiredFields(stage, result, timestamp, contextType);
        validateStageContract(stage, result, durationMs, errorCode);
        validateContextContract(contextType, requestCorrelationId, executionCorrelationId, externalCorrelationId);
        validateErrorCode(errorCode);
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

    private static void validateIdentity(String eventName, String operation) {
        if (!EVENT_NAME.equals(eventName)) {
            throw new IllegalArgumentException("eventName must be AUDIT_OPERATION");
        }
        if (!AuditoriaOperationCatalog.isSupported(operation)) {
            throw new IllegalArgumentException("operation is not in the closed audit catalog");
        }
    }

    private static void validateRequiredFields(Stage stage, Result result, Instant timestamp,
            ContextType contextType) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(contextType, "contextType");
    }

    private static void validateStageContract(Stage stage, Result result, Long durationMs, String errorCode) {
        validateStartContract(stage, result, durationMs, errorCode);
        validateEndContract(stage, result, durationMs, errorCode);
    }

    private static void validateStartContract(Stage stage, Result result, Long durationMs, String errorCode) {
        if (stage == Stage.START && (result != Result.STARTED || durationMs != null || errorCode != null)) {
            throw new IllegalArgumentException("START must be STARTED and have no duration or error code");
        }
    }

    private static void validateEndContract(Stage stage, Result result, Long durationMs, String errorCode) {
        if (stage == Stage.END && (durationMs == null || durationMs < 0
                || (result == Result.ERROR) != (errorCode != null && !errorCode.isBlank())
                || result == Result.STARTED)) {
            throw new IllegalArgumentException("END has an invalid result, duration or error code");
        }
    }

    private static void validateContextContract(ContextType contextType, String requestCorrelationId,
            String executionCorrelationId, String externalCorrelationId) {
        if (contextType == ContextType.HTTP) {
            validateHttpContext(requestCorrelationId, executionCorrelationId, externalCorrelationId);
        } else {
            validateBackgroundContext(requestCorrelationId, executionCorrelationId, externalCorrelationId);
        }
    }

    private static void validateHttpContext(String requestCorrelationId, String executionCorrelationId,
            String externalCorrelationId) {
        if (isBlank(requestCorrelationId) || executionCorrelationId != null) {
            throw new IllegalArgumentException("HTTP requires only requestCorrelationId");
        }
        if (externalCorrelationId != null && !CorrelacionContext.isValidExternalCorrelationId(externalCorrelationId)) {
            throw new IllegalArgumentException("externalCorrelationId is invalid");
        }
    }

    private static void validateBackgroundContext(String requestCorrelationId, String executionCorrelationId,
            String externalCorrelationId) {
        if (isBlank(executionCorrelationId) || requestCorrelationId != null || externalCorrelationId != null) {
            throw new IllegalArgumentException("BACKGROUND requires only executionCorrelationId");
        }
    }

    private static void validateErrorCode(String errorCode) {
        if (errorCode != null && !AuditoriaErrorCodeResolver.isControlledCode(errorCode)) {
            throw new IllegalArgumentException("errorCode is not controlled");
        }
    }
}
