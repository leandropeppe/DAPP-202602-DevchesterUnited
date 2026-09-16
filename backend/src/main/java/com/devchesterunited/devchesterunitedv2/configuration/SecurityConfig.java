package com.devchesterunited.devchesterunitedv2.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorHandler errors,
            Environment environment, ObjectProvider<ApiKeyAuthenticationFilter> apiKeyFilters,
            ObjectProvider<JwtDecoder> decoders) throws Exception {
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .rememberMe(AbstractHttpConfigurer::disable)
                .exceptionHandling(handler -> handler.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .authorizeHttpRequests(access -> {
                    access.requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll();
                    // El controller valida primero que no haya cuerpo y luego exige un JWT.
                    // Esto preserva el 400 contractual para cuerpos no vacíos aun sin credencial.
                    access.requestMatchers(HttpMethod.POST, "/api/v1/auth/apikey").permitAll();
                    access.requestMatchers(HttpMethod.GET, "/api/v1/players").hasAuthority("API_KEY");
                    access.requestMatchers(HttpMethod.POST, "/api/v1/catalog/sync").authenticated();
                    if (environment.acceptsProfiles(Profiles.of("local", "test"))) {
                        if (environment.getProperty("springdoc.api-docs.enabled", Boolean.class, false)) {
                            access.requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll();
                        }
                        if (environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class, false)) {
                            access.requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**").permitAll();
                        }
                    }
                    // Cierre por defecto hasta que cada historia defina su autenticacion.
                    access.anyRequest().denyAll();
                });
        JwtDecoder decoder = decoders.getIfAvailable();
        if (decoder != null) {
            http.oauth2ResourceServer(oauth -> oauth.authenticationEntryPoint(errors).accessDeniedHandler(errors)
                    .jwt(jwt -> jwt.decoder(decoder)));
        }
        ApiKeyAuthenticationFilter apiKeyFilter = apiKeyFilters.getIfAvailable();
        if (apiKeyFilter != null) {
            http.addFilterBefore(apiKeyFilter, BearerTokenAuthenticationFilter.class);
        }
        return http.build();
    }
}
