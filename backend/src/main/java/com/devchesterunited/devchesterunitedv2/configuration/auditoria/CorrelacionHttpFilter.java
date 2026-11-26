package com.devchesterunited.devchesterunitedv2.configuration.auditoria;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Instala la correlación de la solicitud antes de seguridad y la elimina después de cada solicitud HTTP. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelacionHttpFilter extends OncePerRequestFilter {
    static final String CORRELATION_HEADER = "X-Correlation-ID";
    static final String REQUEST_CORRELATION_KEY = "requestCorrelationId";
    static final String EXTERNAL_CORRELATION_KEY = "externalCorrelationId";
    static final String CONTEXT_TYPE_KEY = "contextType";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        CorrelacionContext context = CorrelacionContext.installHttp(request.getHeader(CORRELATION_HEADER));
        String requestCorrelationId = context.requestCorrelationId();
        MDC.put(REQUEST_CORRELATION_KEY, requestCorrelationId);
        MDC.put(CONTEXT_TYPE_KEY, context.contextType().name());
        if (context.externalCorrelationId() == null) {
            MDC.remove(EXTERNAL_CORRELATION_KEY);
        } else {
            MDC.put(EXTERNAL_CORRELATION_KEY, context.externalCorrelationId());
        }
        response.setHeader(CORRELATION_HEADER, requestCorrelationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_CORRELATION_KEY);
            MDC.remove(EXTERNAL_CORRELATION_KEY);
            MDC.remove(CONTEXT_TYPE_KEY);
            CorrelacionContext.clear();
        }
    }
}
