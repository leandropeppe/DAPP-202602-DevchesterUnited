package com.devchesterunited.devchesterunitedv2.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile({"local", "test"})
public class OpenApiConfig {
    @Bean
    public OpenAPI openApi() {
        // Solo declara esquemas. Cada historia asignara el suyo a sus operaciones.
        return new OpenAPI()
                .info(new Info().title("Devchester United API").version("v1")
                        .description("API de Devchester United"))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
                        .addSecuritySchemes("ApiKeyAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-API-KEY")));
    }
}
