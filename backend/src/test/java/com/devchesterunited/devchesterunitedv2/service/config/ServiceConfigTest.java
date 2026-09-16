package com.devchesterunited.devchesterunitedv2.service.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;

class ServiceConfigTest {
    @Test
    void providesInjectableUtcClockAndBcryptWithCost12() {
        try (var context = new AnnotationConfigApplicationContext(ServiceConfig.class)) {
            Clock clock = context.getBean(Clock.class);
            assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
            Instant before = Instant.now();
            Instant current = clock.instant();
            assertThat(current).isBetween(before, Instant.now());

            PasswordEncoder encoder = context.getBean(PasswordEncoder.class);
            String password = "FicticiaSoloTest123";
            String encoded = encoder.encode(password);
            assertThat(encoded).startsWith("$2a$12$").hasSize(60);
            assertThat(encoder.matches(password, encoded)).isTrue();
            assertThat(encoder.matches("Incorrecta123", encoded)).isFalse();
            assertThat(encoder.encode(password)).isNotEqualTo(encoded);
        }
    }
}
