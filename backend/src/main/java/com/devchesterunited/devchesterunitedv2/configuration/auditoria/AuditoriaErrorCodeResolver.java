package com.devchesterunited.devchesterunitedv2.configuration.auditoria;

import java.util.Set;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

/** Resolves stable codes from exception types only; messages and causes are never inspected. */
public final class AuditoriaErrorCodeResolver {
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String AUTHENTICATION_ERROR = "AUTHENTICATION_ERROR";
    public static final String AUTHORIZATION_ERROR = "AUTHORIZATION_ERROR";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String CONFLICT = "CONFLICT";
    public static final String CONCURRENCY_ERROR = "CONCURRENCY_ERROR";
    public static final String EXTERNAL_PROVIDER_ERROR = "EXTERNAL_PROVIDER_ERROR";
    public static final String PERSISTENCE_ERROR = "PERSISTENCE_ERROR";
    public static final String UNEXPECTED_ERROR = "UNEXPECTED_ERROR";
    private static final Set<String> CONTROLLED = Set.of(VALIDATION_ERROR, AUTHENTICATION_ERROR, AUTHORIZATION_ERROR,
            NOT_FOUND, CONFLICT, CONCURRENCY_ERROR, EXTERNAL_PROVIDER_ERROR, PERSISTENCE_ERROR, UNEXPECTED_ERROR);

    public String resolve(Throwable error) {
        if (error == null) return UNEXPECTED_ERROR;
        if (error instanceof AuthenticationException) return AUTHENTICATION_ERROR;
        if (error instanceof AccessDeniedException) return AUTHORIZATION_ERROR;
        if (error instanceof ConcurrencyFailureException) return CONCURRENCY_ERROR;
        if (error instanceof DataAccessException) return PERSISTENCE_ERROR;
        if (error instanceof IllegalArgumentException) return VALIDATION_ERROR;
        String name = error.getClass().getSimpleName();
        if (name.contains("NoEncontrado") || name.contains("NotFound")) return NOT_FOUND;
        if (name.contains("EnCurso") || name.contains("Conflict")) return CONFLICT;
        if (name.contains("Proveedor") || name.contains("Provider") || name.contains("External")) return EXTERNAL_PROVIDER_ERROR;
        return UNEXPECTED_ERROR;
    }

    public static boolean isControlledCode(String code) { return CONTROLLED.contains(code); }
}
