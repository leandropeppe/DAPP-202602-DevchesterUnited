package com.devchesterunited.devchesterunitedv2.service.config;

import com.devchesterunited.devchesterunitedv2.persistence.EstrategiaValorizacionRepository;
import com.devchesterunited.devchesterunitedv2.persistence.EstrategiaVisibleRepository;
import com.devchesterunited.devchesterunitedv2.service.impl.EstrategiaValorizacionBootstrap;
import java.time.Clock;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
public class ValorizacionBootstrapConfig {
    @Bean
    public EstrategiaValorizacionBootstrap estrategiaValorizacionBootstrap(
            EstrategiaValorizacionRepository estrategias, EstrategiaVisibleRepository visibles,
            EstrategiasIniciales iniciales, Clock clock) {
        return new EstrategiaValorizacionBootstrap(estrategias, visibles, iniciales, clock);
    }

    @Bean
    @Profile("!test")
    public ApplicationRunner estrategiaValorizacionBootstrapRunner(EstrategiaValorizacionBootstrap bootstrap) {
        return (ApplicationArguments args) -> bootstrap.sincronizar();
    }
}
