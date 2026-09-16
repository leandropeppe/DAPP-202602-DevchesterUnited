package com.devchesterunited.devchesterunitedv2.controller;

import com.devchesterunited.devchesterunitedv2.configuration.OpenApiConfig;
import com.devchesterunited.devchesterunitedv2.configuration.SecurityConfig;
import com.devchesterunited.devchesterunitedv2.configuration.SecurityErrorHandler;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OpenApiProfilesE2ETest {
    @ParameterizedTest
    @ValueSource(strings = {"local", "test", "prod", ""})
    void documentationRespectsActualApplicationProfileConfiguration(String profile) throws Exception {
        // Lee la configuracion productiva real para no heredar Swagger=true del recurso de tests.
        // Este contexto solo verifica HTTP/documentacion; la suite completa verifica PostgreSQL.
        try (var context = new SpringApplicationBuilder(DocsApplication.class).run(
                "--spring.config.location=file:src/main/resources/application.properties",
                "--spring.profiles.active=" + profile, "--server.port=0")) {
            var mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                    .apply(springSecurity()).build();
            boolean enabled = profile.equals("local") || profile.equals("test");
            assertThat(context.getEnvironment().getProperty("springdoc.api-docs.enabled", Boolean.class))
                    .isEqualTo(enabled);
            assertThat(context.getEnvironment().getProperty("springdoc.swagger-ui.enabled", Boolean.class))
                    .isEqualTo(enabled);
            if (enabled) {
                mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                        .andExpect(jsonPath("$.components.securitySchemes.BearerAuth.type").value("http"))
                        .andExpect(jsonPath("$.components.securitySchemes.BearerAuth.scheme").value("bearer"))
                        .andExpect(jsonPath("$.components.securitySchemes.BearerAuth.bearerFormat").value("JWT"))
                        .andExpect(jsonPath("$.components.securitySchemes.ApiKeyAuth.type").value("apiKey"))
                        .andExpect(jsonPath("$.components.securitySchemes.ApiKeyAuth.in").value("header"))
                        .andExpect(jsonPath("$.components.securitySchemes.ApiKeyAuth.name").value("X-API-KEY"))
                        .andExpect(jsonPath("$.security").doesNotExist());
                mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
                assertThat(context.getBean(OpenAPI.class).getSecurity()).isNull();
            } else {
                mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
                mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
                assertThat(context.getBeansOfType(OpenAPI.class)).isEmpty();
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class, FlywayAutoConfiguration.class})
    @Import({SecurityConfig.class, SecurityErrorHandler.class, OpenApiConfig.class})
    static class DocsApplication { }
}
