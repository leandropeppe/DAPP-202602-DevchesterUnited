package com.devchesterunited.devchesterunitedv2.configuration.valorizacion;

import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.MotorValorizacion;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ValorizacionMotorConfig {
    @Bean
    public MotorValorizacion motorValorizacion() {
        return new MotorValorizacion();
    }
}
