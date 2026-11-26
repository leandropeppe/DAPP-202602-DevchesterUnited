package com.devchesterunited.devchesterunitedv2.unit.auditoria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaErrorCodeResolver;
import org.junit.jupiter.api.Test;
import org.springframework.dao.ConcurrencyFailureException;

class AuditoriaErrorCodeResolverTest {
    @Test void resolvesByTypeAndNotByMessageOrCause() {
        Throwable first = new ConcurrencyFailureException("secret-one", new IllegalStateException("secret-cause"));
        Throwable second = new ConcurrencyFailureException("secret-two", new IllegalArgumentException("other-cause"));
        assertEquals(AuditoriaErrorCodeResolver.CONCURRENCY_ERROR, AuditoriaErrorCodeResolver.resolve(first));
        assertEquals(AuditoriaErrorCodeResolver.resolve(first), AuditoriaErrorCodeResolver.resolve(second));
    }

    @Test void usesControlledFallback() {
        assertEquals(AuditoriaErrorCodeResolver.UNEXPECTED_ERROR,
                AuditoriaErrorCodeResolver.resolve(new AssertionError("hidden")));
        assertEquals(AuditoriaErrorCodeResolver.UNEXPECTED_ERROR, AuditoriaErrorCodeResolver.resolve(null));
    }
}
