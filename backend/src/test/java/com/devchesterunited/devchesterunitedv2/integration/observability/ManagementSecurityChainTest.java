package com.devchesterunited.devchesterunitedv2.integration.observability;

import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "management.server.address=0.0.0.0",
        "management.endpoints.web.exposure.include=health,prometheus",
        "token-market.initialization.auto-start=false"
})
class ManagementSecurityChainTest extends PostgresTestSupport {

    private static final List<String> APPROVED_ACTUATOR_PATHS = List.of(
            "/actuator/health", "/actuator/prometheus");
    private static final List<String> UNAPPROVED_MANAGEMENT_PATHS = List.of(
            "/actuator", "/actuator/env");
    private static final List<String> BUSINESS_PATHS = List.of(
            "/api/v1/players", "/api/v1/market/balance");
    private static final String PASSWORD = "ManagementSecurity123!";

    @LocalServerPort
    private int businessPort;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> fixtureUsers = new ArrayList<>();

    @Test
    void actuatorRoutesAreAuthorizedOnlyOnTheManagementListener() {
        assertThat(managementPort).isPositive().isNotEqualTo(businessPort);

        for (String path : APPROVED_ACTUATOR_PATHS) {
            assertThat(get(managementPort, path).getStatusCode())
                    .as("management listener %s", path)
                    .isEqualTo(HttpStatus.OK);
            assertThat(get(businessPort, path).getStatusCode())
                    .as("business listener %s", path)
                    .isNotEqualTo(HttpStatus.OK);
        }
    }

    @Test
    void businessCredentialsCannotBypassTheManagementListenerOrReachBusinessRoutes() throws Exception {
        BusinessCredentials credentials = registerLoginAndIssueApiKey();

        for (String path : BUSINESS_PATHS) {
            assertRejected(getWithHeader(managementPort, path, "Authorization", "Bearer " + credentials.jwt()));
            assertRejected(getWithHeader(managementPort, path, "X-API-KEY", credentials.apiKey()));
        }

        for (String path : UNAPPROVED_MANAGEMENT_PATHS) {
            assertRejected(getWithHeader(managementPort, path, "Authorization", "Bearer " + credentials.jwt()));
            assertRejected(getWithHeader(managementPort, path, "X-API-KEY", credentials.apiKey()));
        }

        for (String path : APPROVED_ACTUATOR_PATHS) {
            assertThat(getWithHeader(businessPort, path, "Authorization", "Bearer " + credentials.jwt()).getStatusCode())
                    .as("JWT cannot authorize %s on the business listener", path)
                    .isNotEqualTo(HttpStatus.OK);
            assertThat(getWithHeader(businessPort, path, "X-API-KEY", credentials.apiKey()).getStatusCode())
                    .as("API key cannot authorize %s on the business listener", path)
                    .isNotEqualTo(HttpStatus.OK);
        }
    }

    private ResponseEntity<String> get(int port, String path) {
        return restTemplate.getForEntity(url(port, path), String.class);
    }

    private ResponseEntity<String> getWithHeader(int port, String path, String headerName, String headerValue) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(headerName, headerValue);
        return restTemplate.exchange(url(port, path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private void assertRejected(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    private String url(int port, String path) {
        return "http://localhost:" + port + path;
    }

    private BusinessCredentials registerLoginAndIssueApiKey() throws Exception {
        String username = "obs" + UUID.randomUUID().toString().replace("-", "").substring(0, 17);
        JsonNode registration = json(postJson(businessPort, "/api/v1/auth/register", Map.of(
                "username", username,
                "email", username + "@example.test",
                "password", PASSWORD)));
        fixtureUsers.add(UUID.fromString(registration.path("data").path("id").asText().substring(4)));

        JsonNode login = json(postJson(businessPort, "/api/v1/auth/login", Map.of(
                "username", username,
                "password", PASSWORD)));
        String jwt = login.path("data").path("access_token").asText();
        ResponseEntity<String> apiKeyResponse = postWithHeader(
                businessPort, "/api/v1/auth/apikey", "Authorization", "Bearer " + jwt);

        assertThat(apiKeyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        return new BusinessCredentials(jwt, json(apiKeyResponse).path("data").path("api_key").asText());
    }

    private ResponseEntity<String> postJson(int port, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url(port, path), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> postWithHeader(int port, String path, String headerName, String headerValue) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(headerName, headerValue);
        return restTemplate.postForEntity(url(port, path), new HttpEntity<>(headers), String.class);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    @AfterEach
    void cleanAuthenticationFixtures() {
        for (UUID userId : fixtureUsers) {
            jdbc.update("DELETE FROM api_keys WHERE user_id = ?", userId);
            limpiarUsuarioYDependencias(jdbc, userId);
        }
        fixtureUsers.clear();
    }

    private record BusinessCredentials(String jwt, String apiKey) { }
}
