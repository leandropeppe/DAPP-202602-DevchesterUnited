package com.devchesterunited.devchesterunitedv2.integration.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "management.server.address=0.0.0.0",
        "management.endpoints.web.exposure.include=health",
        "management.endpoint.health.show-details=never",
        "token-market.initialization.auto-start=false"
})
class HealthEndpointSecurityTest {

    @LocalServerPort
    private int businessPort;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void healthIsAvailableOnManagementPortWithASummarizedStatus() throws Exception {
        ResponseEntity<String> response = get(managementPort, "/actuator/health");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("status").isTextual()).isTrue();
        assertThat(body.path("status").asText()).isEqualTo("UP");
        assertThat(body.has("details")).isFalse();
        assertThat(response.getBody()).doesNotContainIgnoringCase(
                "password", "secret", "token", "api_key", "jdbc:", "postgresql://", "mysql://");
    }

    @Test
    void healthIsNotAuthorizedOnBusinessPort() {
        ResponseEntity<String> response = get(businessPort, "/actuator/health");

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.OK);
    }

    @Test
    void unapprovedManagementPathsAreRejectedBySecurity() {
        for (String path : new String[]{
                "/actuator/env",
                "/actuator/beans",
                "/actuator/configprops"}) {
            ResponseEntity<String> response = get(managementPort, path);

            assertThat(response.getStatusCode())
                    .as("management path %s", path)
                    .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
            assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void healthResponseIsValidJsonWithoutInternalDetails() throws Exception {
        ResponseEntity<String> response = get(managementPort, "/actuator/health");

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.isObject()).isTrue();
        assertThat(body.fieldNames()).toIterable().contains("status");
        assertThat(body.has("details")).isFalse();
    }

    private ResponseEntity<String> get(int port, String path) {
        return restTemplate.getForEntity("http://localhost:" + port + path, String.class);
    }
}
