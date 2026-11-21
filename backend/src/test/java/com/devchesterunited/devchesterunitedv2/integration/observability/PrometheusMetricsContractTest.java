package com.devchesterunited.devchesterunitedv2.integration.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
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
        "token-market.initialization.auto-start=false"
})
class PrometheusMetricsContractTest {

    private static final String PROMETHEUS_PATH = "/actuator/prometheus";

    @LocalServerPort
    private int businessPort;

    @LocalManagementPort
    private int managementPort;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @Test
    void prometheusReturnsAValidJvmMemoryAndHttpContractOnManagementPort() {
        generateControlledTraffic();

        ResponseEntity<String> response = get(managementPort, PROMETHEUS_PATH);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString())
                .containsAnyOf("text/plain", "application/openmetrics-text");
        assertThat(response.getBody()).isNotBlank();

        String metrics = response.getBody();
        assertThat(metrics).contains("jvm_memory_used_bytes");
        assertThat(metrics).contains("jvm_buffer_memory_used_bytes");
        assertThat(metrics).contains("http_server_requests_seconds");
        assertContainsPrometheusSample(metrics, "jvm_memory_used_bytes");
        assertContainsPrometheusSample(metrics, "jvm_buffer_memory_used_bytes");
        assertContainsPrometheusSample(metrics, "http_server_requests_seconds_count");
    }

    @Test
    void prometheusIsNotAvailableOnBusinessPort() {
        ResponseEntity<String> response = get(businessPort, PROMETHEUS_PATH);

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.OK);
    }

    private void generateControlledTraffic() {
        get(businessPort, "/api/v1/players");
        get(businessPort, "/api/v1/players?prometheus-contract=controlled");
    }

    private ResponseEntity<String> get(int port, String path) {
        return restTemplate.getForEntity("http://localhost:" + port + path, String.class);
    }

    private void assertContainsPrometheusSample(String metrics, String metricName) {
        String samplePattern = "(?m)^" + Pattern.quote(metricName)
                + "(?:\\{[^\\r\\n]*\\})?\\s+"
                + "(?:[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][-+]?\\d+)?|NaN|Inf|-Inf)"
                + "(?:\\s+\\d+)?\\s*$";

        assertThat(metrics).containsPattern(samplePattern);
    }
}
