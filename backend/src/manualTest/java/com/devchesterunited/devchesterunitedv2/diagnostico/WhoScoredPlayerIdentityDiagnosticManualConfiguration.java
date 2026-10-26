package com.devchesterunited.devchesterunitedv2.diagnostico;

import com.devchesterunited.devchesterunitedv2.adapter.WhoScoredStatisticsConfiguration;
import com.devchesterunited.devchesterunitedv2.adapter.impl.SeleniumWebDriverFactory;
import com.devchesterunited.devchesterunitedv2.adapter.impl.WhoScoredStatisticsAdapter;
import com.devchesterunited.devchesterunitedv2.persistence.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.IdentidadExternaJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.service.diagnostico.WhoscoredPlayerIdentityDiagnosticService;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Minimal manual context: read repositories, provider, and diagnostic service only. */
@TestConfiguration(proxyBeanMethods = false)
@ImportAutoConfiguration({ConfigurationPropertiesAutoConfiguration.class,
        PropertyPlaceholderAutoConfiguration.class, DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
        JacksonAutoConfiguration.class, RestClientAutoConfiguration.class, TransactionAutoConfiguration.class})
@EntityScan(basePackageClasses = EquipoEntity.class)
@EnableJpaRepositories(basePackageClasses = JugadorRepository.class,
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
                IdentidadExternaEquipoRepository.class,
                IdentidadExternaJugadorRepository.class, JugadorRepository.class}))
@Import({WhoScoredStatisticsConfiguration.class, WhoscoredPlayerIdentityDiagnosticService.class,
        WhoScoredStatisticsAdapter.class, SeleniumWebDriverFactory.class})
public class WhoScoredPlayerIdentityDiagnosticManualConfiguration { }
