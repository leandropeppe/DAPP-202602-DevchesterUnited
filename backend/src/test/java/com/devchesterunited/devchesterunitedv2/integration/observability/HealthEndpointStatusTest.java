package com.devchesterunited.devchesterunitedv2.integration.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@Import(HealthEndpointStatusTest.UnhealthyDependencyConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "management.server.address=0.0.0.0",
        "management.endpoints.web.exposure.include=health",
        "management.endpoint.health.show-details=never",
        "token-market.initialization.auto-start=false"
})
class HealthEndpointStatusTest {

    @LocalServerPort
    private int businessPort;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void unhealthyDependencyProducesSafeDegradedHealth() throws Exception {
        ResponseEntity<String> response = get(managementPort, "/actuator/health");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("status").asText()).isEqualTo("DOWN");
        assertThat(body.has("details")).isFalse();
        assertThat(response.getBody()).doesNotContainIgnoringCase(
                "password", "secret", "token", "api_key", "jdbc:", "postgresql://");
        assertThat(get(businessPort, "/actuator/health").getStatusCode()).isNotEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> get(int port, String path) {
        return restTemplate.getForEntity("http://localhost:" + port + path, String.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class UnhealthyDependencyConfiguration {

        @Bean
        HealthIndicator unhealthyDependencyHealthIndicator() {
            return () -> Health.down()
                    .withDetail("jdbcUrl", "jdbc:postgresql://internal.example/hidden")
                    .withDetail("password", "must-not-be-exposed")
                    .withDetail("token", "must-not-be-exposed")
                    .build();
        }
    }
}
