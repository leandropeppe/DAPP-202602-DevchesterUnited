package com.devchesterunited.devchesterunitedv2.unit.valorizacion;

import com.devchesterunited.devchesterunitedv2.configuration.valorizacion.ValorizacionMotorConfig;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.MotorValorizacion;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class ValorizacionMotorConfigTest {
    @Test
    void registraUnUnicoMotorValorizacionEnElContexto() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(ValorizacionMotorConfig.class)) {
            assertThat(context.getBeansOfType(MotorValorizacion.class)).hasSize(1);
            assertThat(context.getBean(MotorValorizacion.class)).isNotNull();
        }
    }
}
