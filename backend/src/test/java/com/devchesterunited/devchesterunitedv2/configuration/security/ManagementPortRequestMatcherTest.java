package com.devchesterunited.devchesterunitedv2.configuration.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.WebApplicationContext;

class ManagementPortRequestMatcherTest {

    private static final int BUSINESS_PORT = 8080;
    private static final int MANAGEMENT_PORT = 8081;
    private static final int UNTRUSTED_FORWARDED_PORT = 65535;
    private static final List<String> APPROVED_PATHS = List.of(
            "/actuator/health", "/actuator/prometheus");

    @Test
    void identifiesTheManagementContextForDenialBeforeTheEffectivePortIsKnown() {
        WebApplicationContext mainContext = mock(WebApplicationContext.class);
        ServletWebServerApplicationContext managementContext = managementContextOf(mainContext);
        ManagementPortRequestMatcher matcher = new ManagementPortRequestMatcher(mainContext);

        for (String path : APPROVED_PATHS) {
            MockHttpServletRequest request = requestFor(
                    managementContext, MANAGEMENT_PORT, path, UNTRUSTED_FORWARDED_PORT);

            assertThat(matcher.matches(request)).isTrue();
            assertThat(matcher.matchesKnownPort(request)).isFalse();
        }

        assertThat(matcher.matches(requestFor(
                mainContext, BUSINESS_PORT, "/actuator/health", UNTRUSTED_FORWARDED_PORT))).isFalse();
        assertThat(matcher.matchesKnownPort(requestFor(
                mainContext, BUSINESS_PORT, "/actuator/health", UNTRUSTED_FORWARDED_PORT)))
                .isFalse();
    }

    @Test
    void acceptsOnlyTheEffectiveManagementPortWithoutTrustingForwardedHeaders() {
        WebApplicationContext mainContext = mock(WebApplicationContext.class);
        ServletWebServerApplicationContext managementContext = managementContextOf(mainContext);
        ManagementPortRequestMatcher matcher = new ManagementPortRequestMatcher(mainContext);

        publishManagementPort(matcher, managementContext);

        for (String path : APPROVED_PATHS) {
            MockHttpServletRequest managementRequest = requestFor(
                    managementContext, MANAGEMENT_PORT, path, BUSINESS_PORT);
            assertThat(matcher.matchesKnownPort(managementRequest)).isTrue();

            MockHttpServletRequest businessRequest = requestFor(
                    managementContext, BUSINESS_PORT, path, MANAGEMENT_PORT);
            assertThat(matcher.matchesKnownPort(businessRequest)).isFalse();
        }

        assertThat(matcher.matchesKnownPort(requestFor(
                mainContext, MANAGEMENT_PORT, "/actuator/health", UNTRUSTED_FORWARDED_PORT)))
                .isFalse();
    }

    private ServletWebServerApplicationContext managementContextOf(WebApplicationContext mainContext) {
        ServletWebServerApplicationContext managementContext = mock(ServletWebServerApplicationContext.class);
        when(managementContext.getParent()).thenReturn(mainContext);
        return managementContext;
    }

    private MockHttpServletRequest requestFor(WebApplicationContext context, int localPort, String path,
            int forwardedPort) {
        MockServletContext servletContext = new MockServletContext();
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.setLocalPort(localPort);
        request.setRequestURI(path);
        request.addHeader("Host", "untrusted.example:" + forwardedPort);
        request.addHeader("X-Forwarded-Port", forwardedPort);
        return request;
    }

    private void publishManagementPort(ManagementPortRequestMatcher matcher,
            ServletWebServerApplicationContext managementContext) {
        WebServer managementServer = mock(WebServer.class);
        when(managementServer.getPort()).thenReturn(MANAGEMENT_PORT);
        WebServerInitializedEvent event = mock(WebServerInitializedEvent.class);
        when(event.getApplicationContext()).thenReturn(managementContext);
        when(event.getWebServer()).thenReturn(managementServer);
        matcher.onApplicationEvent(event);
    }
}
