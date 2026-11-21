package com.devchesterunited.devchesterunitedv2.integration.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.WebApplicationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.devchesterunited.devchesterunitedv2.configuration.security.ManagementPortRequestMatcher;
import com.devchesterunited.devchesterunitedv2.service.interfaces.valorizacion.ValorizacionEjecucionPort;
import com.devchesterunited.test.observability.RequestServletContextProbe;
import com.devchesterunited.test.observability.RequestServletContextProbeFilter;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@Import(ManagementSecurityFoundationTest.ManagementEventsConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "management.server.address=0.0.0.0",
        "management.prometheus.metrics.export.enabled=true",
        "management.endpoints.web.exposure.include=health,prometheus",
        "token-market.initialization.auto-start=false"
})
class ManagementSecurityFoundationTest {

    @LocalServerPort
    private int businessPort;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ManagementEventRecorder managementEvents;

    @Autowired
    private RequestServletContextProbe requestServletContextProbe;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private ValorizacionEjecucionPort valorizacionEjecucion;

    private final List<String> fixtureUsers = new ArrayList<>();

    @Test
    void managementChildReusesTheMainServletSecurityFilterChain() {
        ApplicationContext managementContext = managementEvent().getApplicationContext();

        assertThat(managementContext).isNotSameAs(applicationContext);
        assertThat(managementContext.getParent()).isSameAs(applicationContext);
        assertThat(((ConfigurableApplicationContext) managementContext).getBeanFactory()
                .containsLocalBean("securityFilterChain")).isFalse();
        assertThat(managementContext.getBean("springSecurityFilterChain"))
                .isSameAs(applicationContext.getBean("springSecurityFilterChain"))
                .isInstanceOf(FilterChainProxy.class);
    }

    @Test
    void realRequestsExposeTheExpectedServletApplicationContext() {
        get(managementPort, "/actuator/health");
        get(businessPort, "/actuator/health");

        RequestServletContextProbe.Observation managementObservation = requestServletContextProbe
                .observationFor(managementPort);
        RequestServletContextProbe.Observation businessObservation = requestServletContextProbe
                .observationFor(businessPort);
        ApplicationContext managementContext = managementEvent().getApplicationContext();

        assertThat(managementObservation.webApplicationContext()).isSameAs(managementContext);
        assertThat(managementObservation.webApplicationContext().getParent()).isSameAs(applicationContext);
        assertThat(businessObservation.webApplicationContext()).isSameAs(applicationContext);
        assertThat(businessObservation.webApplicationContext()).isNotSameAs(managementContext);
        assertThat(businessObservation.webApplicationContext().getParent()).isNotSameAs(applicationContext);
    }

    @Test
    void approvedManagementRoutesArePermittedOnlyOnTheEffectiveManagementPort() {
        assertThat(managementPort).isPositive().isNotEqualTo(businessPort);
        assertThat(get(managementPort, "/actuator/health").getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> prometheus = get(managementPort, "/actuator/prometheus");
        assertThat(prometheus.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(prometheus.getBody()).isNotBlank().contains("jvm_memory_used_bytes");
        assertThat(get(businessPort, "/actuator/health").getStatusCode()).isNotEqualTo(HttpStatus.OK);
        assertThat(get(businessPort, "/actuator/prometheus").getStatusCode()).isNotEqualTo(HttpStatus.OK);
    }

    @Test
    void managementEndpointsRejectNonGetMethods() {
        assertThat(post(managementPort, "/actuator/health").getStatusCode())
                .isIn(HttpStatus.FORBIDDEN, HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(post(managementPort, "/actuator/prometheus").getStatusCode())
                .isIn(HttpStatus.FORBIDDEN, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void unknownManagementRoutesAreRejectedBySecurityNotByA404() {
        for (String path : List.of("/actuator/env", "/actuator/beans", "/actuator/configprops")) {
            ResponseEntity<String> response = get(managementPort, path);

            assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
            assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void businessCredentialsDoNotBypassManagementSecurityOrListenerIsolation() throws Exception {
        String jwt = registerAndLogin();
        String apiKey = apiKeyFor(businessPort, jwt);

        for (String path : List.of("/actuator/env", "/actuator/beans", "/actuator/configprops")) {
            assertThat(getWithHeader(managementPort, path,
                    "Authorization", "Bearer " + jwt).getStatusCode())
                    .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
            assertThat(getWithHeader(managementPort, path, "X-API-KEY", apiKey).getStatusCode())
                    .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
        }

        assertThat(getWithHeader(businessPort, "/actuator/health",
                "Authorization", "Bearer " + jwt).getStatusCode()).isNotEqualTo(HttpStatus.OK);
        assertThat(getWithHeader(businessPort, "/actuator/prometheus",
                "X-API-KEY", apiKey).getStatusCode()).isNotEqualTo(HttpStatus.OK);
    }

    @Test
    void managementListenerCannotReachBusinessApiRoutes() {
        assertThat(get(managementPort, "/api/v1/market/balance").getStatusCode())
                .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    void managementListenerCannotUseBusinessPublicRoutes() {
        assertThat(post(managementPort, "/api/v1/auth/login").getStatusCode())
                .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
        assertThat(get(managementPort, "/api/v1/players").getStatusCode())
                .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);

        assertThat(post(businessPort, "/api/v1/auth/login").getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(businessPort, "/api/v1/players").getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void businessAuthenticationAndAuthorizationRemainClosed() {
        assertThat(get(businessPort, "/api/v1/market/balance").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(getWithHeader(businessPort, "/api/v1/market/balance",
                "Authorization", "Bearer invalid-token").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(postWithHeader(businessPort, "/api/v1/catalog/sync",
                "X-API-KEY", "invalid-key").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(getWithHeader(managementPort, "/api/v1/market/balance",
                "Authorization", "Bearer invalid-token").getStatusCode())
                .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
        assertThat(postWithHeader(managementPort, "/api/v1/catalog/sync",
                "X-API-KEY", "invalid-key").getStatusCode())
                .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    void validJwtAuthorizesBusinessOperationButNotManagementListener() throws Exception {
        String jwt = registerAndLogin();

        assertThat(postWithBearer(businessPort, "/api/v1/auth/apikey", jwt).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(postWithBearer(managementPort, "/api/v1/auth/apikey", jwt).getStatusCode())
                .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    void validApiKeyAuthorizesBusinessOperationButNotManagementListener() throws Exception {
        String jwt = registerAndLogin();
        String apiKey = apiKeyFor(businessPort, jwt);

        assertThat(postWithHeader(businessPort, "/api/v1/quotes/recalculate", "X-API-KEY", apiKey)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(postWithHeader(managementPort, "/api/v1/quotes/recalculate", "X-API-KEY", apiKey)
                .getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    void dynamicManagementPortProducesDistinctBusinessAndChildContextEvents() {
        List<WebServerInitializedEvent> serverEvents = managementEvents.events();

        assertThat(serverEvents).hasSizeGreaterThanOrEqualTo(2);
        assertThat(serverEvents).anySatisfy(event -> assertThat(event.getWebServer().getPort()).isEqualTo(businessPort));
        assertThat(serverEvents).anySatisfy(event -> assertThat(event.getWebServer().getPort()).isEqualTo(managementPort));
        assertThat(managementEvent().getApplicationContext()).isNotSameAs(applicationContext);
        assertThat(managementEvent().getApplicationContext().getParent()).isSameAs(applicationContext);
    }

    @Test
    void managementClassificationFailsClosedBeforeAChildEventProvidesThePort() {
        WebApplicationContext mainContext = mock(WebApplicationContext.class);
        ServletWebServerApplicationContext managementContext = managementContextOf(mainContext);
        ManagementPortRequestMatcher matcher = new ManagementPortRequestMatcher(mainContext);

        for (String path : List.of("/actuator/health", "/actuator/prometheus", "/api/v1/players")) {
            assertThat(matcher.matches(requestFor(managementContext, 8081, path))).isTrue();
        }
    }

    @Test
    void businessContextKeepsItsNormalPolicyBeforeAChildEventProvidesThePort() {
        WebApplicationContext mainContext = mock(WebApplicationContext.class);
        ManagementPortRequestMatcher matcher = new ManagementPortRequestMatcher(mainContext);

        for (String path : List.of("/actuator/health", "/actuator/prometheus", "/api/v1/players")) {
            assertThat(matcher.matches(requestFor(mainContext, 8081, path))).isFalse();
        }
    }

    @Test
    void knownManagementContextIsClassifiedByItsEffectivePort() {
        WebApplicationContext mainContext = mock(WebApplicationContext.class);
        ServletWebServerApplicationContext managementContext = managementContextOf(mainContext);
        ManagementPortRequestMatcher matcher = new ManagementPortRequestMatcher(mainContext);
        WebServer managementServer = mock(WebServer.class);
        when(managementServer.getPort()).thenReturn(8081);
        WebServerInitializedEvent managementEvent = mock(WebServerInitializedEvent.class);
        when(managementEvent.getApplicationContext()).thenReturn(managementContext);
        when(managementEvent.getWebServer()).thenReturn(managementServer);

        matcher.onApplicationEvent(managementEvent);

        assertThat(matcher.matches(requestFor(managementContext, 8081, "/actuator/health"))).isTrue();
        assertThat(matcher.matches(requestFor(managementContext, 8081, "/actuator/prometheus"))).isTrue();
        assertThat(matcher.matches(requestFor(managementContext, 8080, "/actuator/health"))).isFalse();
    }

    @Test
    void managementContextClassificationCannotBeBypassedByBusinessCredentials() {
        WebApplicationContext mainContext = mock(WebApplicationContext.class);
        WebApplicationContext managementContext = managementContextOf(mainContext);
        ManagementPortRequestMatcher matcher = new ManagementPortRequestMatcher(mainContext);
        MockHttpServletRequest request = requestFor(managementContext, 8081, "/api/v1/players");
        request.addHeader("Authorization", "Bearer valid-business-token");
        request.addHeader("X-API-KEY", "valid-business-key");

        assertThat(matcher.matches(request)).isTrue();
    }

    private ServletWebServerApplicationContext managementContextOf(WebApplicationContext mainContext) {
        ServletWebServerApplicationContext managementContext = mock(ServletWebServerApplicationContext.class);
        when(managementContext.getParent()).thenReturn(mainContext);
        return managementContext;
    }

    private MockHttpServletRequest requestFor(WebApplicationContext context, int localPort, String path) {
        MockServletContext servletContext = new MockServletContext();
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
        MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
        request.setLocalPort(localPort);
        request.setRequestURI(path);
        return request;
    }

    private WebServerInitializedEvent managementEvent() {
        return managementEvents.events().stream()
                .filter(event -> event.getWebServer().getPort() == managementPort)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No WebServerInitializedEvent for management port " + managementPort));
    }

    private ResponseEntity<String> get(int port, String path) {
        return restTemplate.getForEntity("http://localhost:" + port + path, String.class);
    }

    private ResponseEntity<String> getWithHeader(int port, String path, String header, String value) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(header, value);
        return restTemplate.exchange("http://localhost:" + port + path, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> post(int port, String path) {
        return restTemplate.postForEntity("http://localhost:" + port + path, null, String.class);
    }

    private ResponseEntity<String> postWithHeader(int port, String path, String header, String value) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(header, value);
        return restTemplate.postForEntity("http://localhost:" + port + path,
                new HttpEntity<>(headers), String.class);
    }

    @AfterEach
    void cleanAuthenticationFixtures() {
        for (String username : fixtureUsers) {
            cleanAuthenticationFixture(username);
        }
        fixtureUsers.clear();
    }

    private void cleanAuthenticationFixture(String username) {
        jdbc.update("DELETE FROM market_initial_credit_grants WHERE user_id IN "
                + "(SELECT id FROM usuarios WHERE username = ?)", username);
        jdbc.update("DELETE FROM market_credit_accounts WHERE user_id IN "
                + "(SELECT id FROM usuarios WHERE username = ?)", username);
        jdbc.update("DELETE FROM api_keys WHERE user_id IN "
                + "(SELECT id FROM usuarios WHERE username = ?)", username);
        jdbc.update("DELETE FROM usuarios WHERE username = ?", username);
    }

    private String registerAndLogin() throws Exception {
        String username = "obs" + UUID.randomUUID().toString().replace("-", "").substring(0, 17);
        String password = "Observability123!";
        fixtureUsers.add(username);
        ResponseEntity<String> registered = postJson(businessPort, "/api/v1/auth/register",
                Map.of("username", username, "email", username + "@example.test", "password", password));
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> login = postJson(businessPort, "/api/v1/auth/login",
                Map.of("username", username, "password", password));
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String jwt = json(login).path("data").path("access_token").asText();
        assertThat(jwt).isNotBlank();
        return jwt;
    }

    private String apiKeyFor(int port, String jwt) throws Exception {
        ResponseEntity<String> response = postWithBearer(port, "/api/v1/auth/apikey", jwt);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String apiKey = json(response).path("data").path("api_key").asText();
        assertThat(apiKey).matches("dev_key_[A-Za-z0-9_-]{43}");
        return apiKey;
    }

    private ResponseEntity<String> postWithBearer(int port, String path, String jwt) {
        return postWithHeader(port, path, "Authorization", "Bearer " + jwt);
    }

    private ResponseEntity<String> postJson(int port, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange("http://localhost:" + port + path, HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ManagementEventsConfiguration {
        @Bean
        ManagementEventRecorder managementEventRecorder() {
            return new ManagementEventRecorder();
        }

        @Bean
        RequestServletContextProbe requestServletContextProbe() {
            return new RequestServletContextProbe();
        }

        @Bean
        FilterRegistrationBean<RequestServletContextProbeFilter> requestServletContextProbeFilter(
                RequestServletContextProbe probe) {
            FilterRegistrationBean<RequestServletContextProbeFilter> registration = new FilterRegistrationBean<>(
                    new RequestServletContextProbeFilter(probe));
            registration.addUrlPatterns("/*");
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }
    }

    static class ManagementEventRecorder implements ApplicationListener<WebServerInitializedEvent> {
        private final List<WebServerInitializedEvent> events = new ArrayList<>();

        @Override
        public synchronized void onApplicationEvent(WebServerInitializedEvent event) {
            events.add(event);
        }

        synchronized List<WebServerInitializedEvent> events() {
            return List.copyOf(events);
        }
    }

}
