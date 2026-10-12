package com.devchesterunited.devchesterunitedv2.importer;

import com.devchesterunited.devchesterunitedv2.persistence.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.service.importer.WhoscoredTeamIdentityImporter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;

@TestConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration(exclude = {FlywayAutoConfiguration.class, SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class})
@EntityScan(basePackageClasses = EquipoEntity.class)
@EnableJpaRepositories(basePackageClasses = EquipoRepository.class,
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {EquipoRepository.class, IdentidadExternaEquipoRepository.class}))
@EnableConfigurationProperties
@Import(WhoscoredTeamIdentityImporter.class)
public class WhoscoredTeamIdentityImporterManualConfiguration {
}
