package com.devchesterunited.devchesterunitedv2.diagnostico;

import com.devchesterunited.devchesterunitedv2.adapter.WhoScoredStatisticsConfiguration;
import com.devchesterunited.devchesterunitedv2.adapter.impl.SeleniumWebDriverFactory;
import com.devchesterunited.devchesterunitedv2.adapter.impl.WhoScoredStatisticsAdapter;
import com.devchesterunited.devchesterunitedv2.persistence.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.service.diagnostico.WhoscoredTeamEnrollmentReportService;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Contexto mínimo del reporte manual; no importa la aplicación web. */
@TestConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration(exclude = {
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class,
        OAuth2ResourceServerAutoConfiguration.class
})
@EntityScan(basePackageClasses = EquipoEntity.class)
@EnableJpaRepositories(
        basePackageClasses = EquipoRepository.class,
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {EquipoRepository.class, IdentidadExternaEquipoRepository.class}))
@EnableConfigurationProperties(WhoScoredStatisticsConfiguration.class)
@Import({
        WhoscoredTeamEnrollmentReportService.class,
        WhoScoredStatisticsAdapter.class,
        SeleniumWebDriverFactory.class
})
public class WhoScoredTeamEnrollmentReportManualConfiguration {
}
