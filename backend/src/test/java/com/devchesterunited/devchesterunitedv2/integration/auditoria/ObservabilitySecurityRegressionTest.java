package com.devchesterunited.devchesterunitedv2.integration.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0", "management.server.address=0.0.0.0",
        "management.endpoints.web.exposure.include=health,prometheus",
        "management.endpoint.health.show-details=never", "token-market.initialization.auto-start=false"
})
class ObservabilitySecurityRegressionTest {
    @LocalServerPort
    private int businessPort;
    @LocalManagementPort
    private int managementPort;
    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthAndPrometheusRemainAvailableOnManagementPort() {
        ResponseEntity<String> health = restTemplate.getForEntity(
                "http://localhost:" + managementPort + "/actuator/health", String.class);
        ResponseEntity<String> prometheus = restTemplate.getForEntity(
                "http://localhost:" + managementPort + "/actuator/prometheus", String.class);

        assertThat(health.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(prometheus.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(prometheus.getBody()).contains("# HELP");
    }

    @Test
    void existingJwtAndApiKeyBoundariesRemainProtectedAndCorrelated() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer synthetic.invalid.jwt");
        headers.set("X-API-KEY", "synthetic-invalid-api-key");
        headers.set("X-Correlation-ID", "client.safe-label");
        ResponseEntity<String> jwtRoute = restTemplate.exchange(
                "http://localhost:" + businessPort + "/api/v1/market/balance",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        ResponseEntity<String> apiKeyRoute = restTemplate.exchange(
                "http://localhost:" + businessPort + "/api/v1/catalog/sync",
                HttpMethod.POST, new HttpEntity<>(headers), String.class);

        assertThat(jwtRoute.getStatusCode().value()).isEqualTo(401);
        assertThat(apiKeyRoute.getStatusCode().value()).isEqualTo(401);
        assertThat(jwtRoute.getHeaders().getFirst("X-Correlation-ID"))
                .isNotBlank().isNotEqualTo("client.safe-label");
        assertThat(apiKeyRoute.getHeaders().getFirst("X-Correlation-ID"))
                .isNotBlank().isNotEqualTo("client.safe-label");
    }
}
