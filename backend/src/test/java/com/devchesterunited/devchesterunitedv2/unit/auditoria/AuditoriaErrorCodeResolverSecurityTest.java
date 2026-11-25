package com.devchesterunited.devchesterunitedv2.unit.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaErrorCodeResolver;
import org.junit.jupiter.api.Test;

class AuditoriaErrorCodeResolverSecurityTest {
    @Test
    void codeDependsOnTypeAndNotMessageOrCauseChain() {
        RuntimeException first = new RuntimeException("password=secret jwt=synthetic");
        RuntimeException second = new RuntimeException("Authorization: Bearer another-secret",
                new IllegalArgumentException("api-key=synthetic"));

        assertThat(AuditoriaErrorCodeResolver.resolve(first)).isEqualTo("UNEXPECTED_ERROR");
        assertThat(AuditoriaErrorCodeResolver.resolve(second)).isEqualTo("UNEXPECTED_ERROR");
        assertThat(AuditoriaErrorCodeResolver.resolve(null)).isEqualTo("UNEXPECTED_ERROR");
        assertThat(AuditoriaErrorCodeResolver.isControlledCode("password=secret")).isFalse();
    }
}
