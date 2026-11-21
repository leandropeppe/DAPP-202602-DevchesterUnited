package com.devchesterunited.devchesterunitedv2.configuration.security;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

/**
 * Matches requests only after the child management context has published its
 * effective port.
 */
@Component
public final class ManagementPortRequestMatcher
        implements RequestMatcher, ApplicationListener<WebServerInitializedEvent> {

    private static final int UNKNOWN_PORT = -1;

    private final ApplicationContext mainContext;
    private final AtomicInteger managementPort = new AtomicInteger(UNKNOWN_PORT);

    public ManagementPortRequestMatcher(ApplicationContext mainContext) {
        this.mainContext = mainContext;
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        WebApplicationContext requestContext = WebApplicationContextUtils
                .getWebApplicationContext(request.getServletContext());
        if (!isManagementContext(requestContext)) {
            return false;
        }
        int knownPort = managementPort.get();
        return knownPort == UNKNOWN_PORT || request.getLocalPort() == knownPort;
    }

    boolean matchesKnownPort(HttpServletRequest request) {
        WebApplicationContext requestContext = WebApplicationContextUtils
                .getWebApplicationContext(request.getServletContext());
        if (!isManagementContext(requestContext)) {
            return false;
        }
        int knownPort = managementPort.get();
        return knownPort != UNKNOWN_PORT && request.getLocalPort() == knownPort;
    }

    private boolean isManagementContext(WebApplicationContext requestContext) {
        return requestContext != null
                && requestContext != mainContext
                && requestContext.getParent() == mainContext;
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        ApplicationContext eventContext = event.getApplicationContext();
        if (eventContext != mainContext && eventContext.getParent() == mainContext) {
            managementPort.compareAndSet(UNKNOWN_PORT, event.getWebServer().getPort());
        }
    }
}
