package com.devchesterunited.devchesterunitedv2.configuration;

import com.devchesterunited.devchesterunitedv2.service.interfaces.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    private static final RequestMatcher ENDPOINT_SINCRONIZACION =
            PathPatternRequestMatcher.withDefaults()
                    .matcher(HttpMethod.POST, "/api/v1/catalog/sync");
    private final ApiKeyService apiKeys;
    private final SecurityErrorHandler errors;

    public ApiKeyAuthenticationFilter(ObjectProvider<ApiKeyService> apiKeys,
            ObjectProvider<SecurityErrorHandler> errors) {
        this.apiKeys = apiKeys.getIfAvailable();
        this.errors = errors.getIfAvailable();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!esEndpointAdministrativo(request)) {
            chain.doFilter(request, response);
            return;
        }
        if (apiKeys == null || errors == null) {
            chain.doFilter(request, response);
            return;
        }
        String presented = request.getHeader("X-API-KEY");
        if (presented == null || presented.isBlank()) {
            errors.commence(request, response, new BadCredentialsException("API Key ausente."));
            return;
        }
        try {
            UUID usuario = apiKeys.validar(presented);
            var autenticacion = new UsernamePasswordAuthenticationToken(usuario, null,
                    List.of(new SimpleGrantedAuthority("API_KEY")));
            autenticacion.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(autenticacion);
            chain.doFilter(request, response);
        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            errors.commence(request, response, exception);
        } catch (DataAccessException exception) {
            SecurityContextHolder.clearContext();
            errors.serviceUnavailable(request, response);
        }
    }

    private static boolean esEndpointAdministrativo(HttpServletRequest request) {
        return ENDPOINT_SINCRONIZACION.matches(request);
    }
}
