package com.devchesterunited.devchesterunitedv2.integration.observability;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "management.server.address=0.0.0.0",
        "management.endpoints.web.exposure.include=health,prometheus",
        "token-market.initialization.auto-start=false"
})
class ManagementExposureTest {

    private static final List<String> APPROVED_PATHS = List.of(
            "/actuator/health", "/actuator/prometheus");
    private static final List<String> UNAPPROVED_PATHS = List.of(
            "/actuator",
            "/actuator/env",
            "/actuator/beans",
            "/actuator/configprops",
            "/actuator/mappings",
            "/actuator/loggers",
            "/actuator/threaddump",
            "/actuator/metrics",
            "/actuator/not-approved");

    @LocalServerPort
    private int businessPort;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void onlyApprovedActuatorEndpointsAreAvailableOnTheManagementListener() {
        for (String path : APPROVED_PATHS) {
            assertThat(get(managementPort, path).getStatusCode())
                    .as("approved management endpoint %s", path)
                    .isEqualTo(HttpStatus.OK);
        }

        for (String path : UNAPPROVED_PATHS) {
            ResponseEntity<String> response = get(managementPort, path);

            assertThat(response.getStatusCode())
                    .as("unapproved management endpoint %s", path)
                    .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
            assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void businessListenerNeverExposesActuatorEndpoints() {
        for (String path : APPROVED_PATHS) {
            assertThat(get(businessPort, path).getStatusCode())
                    .as("business listener %s", path)
                    .isNotEqualTo(HttpStatus.OK);
        }
    }

    private ResponseEntity<String> get(int port, String path) {
        return restTemplate.getForEntity("http://localhost:" + port + path, String.class);
    }
}
