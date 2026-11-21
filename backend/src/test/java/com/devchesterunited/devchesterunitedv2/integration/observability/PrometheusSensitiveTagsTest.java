package com.devchesterunited.devchesterunitedv2.integration.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "management.server.address=0.0.0.0",
        "token-market.initialization.auto-start=false"
})
class PrometheusSensitiveTagsTest {

    private static final String QUERY_VALUE = "prometheus-query-sensitive-7f31";
    private static final String AUTHORIZATION_VALUE = "Bearer-prometheus-sensitive-91a2";
    private static final String API_KEY_VALUE = "prometheus-api-key-sensitive-4c83";
    private static final String COOKIE_VALUE = "prometheus-cookie-sensitive-b812";
    private static final String TOKEN_VALUE = "prometheus-token-sensitive-5d64";
    private static final String PAYLOAD_VALUE = "prometheus-payload-sensitive-a927";
    private static final String VARIABLE_PATH_VALUE = "00000000-0000-0000-0000-000000000017";
    private static final String EMAIL_VALUE = "prometheus.user@example.invalid";
    private static final String SENSITIVE_URL_VALUE = "https://sensitive.example.invalid/private?token=" + TOKEN_VALUE;
    private static final String PERSONAL_DATA_VALUE = "prometheus-personal-data-a927";

    private static final Set<String> ALLOWED_HTTP_TAGS = Set.of(
            "error", "exception", "method", "outcome", "status", "uri");
    private static final Pattern HTTP_SAMPLE_PATTERN = Pattern.compile(
            "^http_server_requests_seconds(?:_(?:bucket|count|created|max|sum))?\\{(?<labels>.*)\\}\\s+.*$");
    private static final Pattern LABEL_PATTERN = Pattern.compile(
            "(?:^|,)\\s*([A-Za-z_][A-Za-z0-9_]*)=\"((?:\\\\.|[^\"\\\\])*)\"");

    @LocalServerPort
    private int businessPort;

    @LocalManagementPort
    private int managementPort;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @Test
    void prometheusDoesNotExposeSensitiveRequestValuesOrHighCardinalityValues() {
        generateRequestsContainingSensitiveInputs();

        ResponseEntity<String> response = get(managementPort, "/actuator/prometheus");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        String metrics = response.getBody();
        assertThat(metrics).contains("http_server_requests_seconds");
        assertMetricsDoNotContainSensitiveValues(metrics);
        assertHttpDimensionsAreNotSensitiveOrHighCardinality(metrics);
    }

    private void generateRequestsContainingSensitiveInputs() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, AUTHORIZATION_VALUE);
        headers.set("X-API-Key", API_KEY_VALUE);
        headers.set(HttpHeaders.COOKIE, "session=" + COOKIE_VALUE);
        headers.set("X-Request-Token", TOKEN_VALUE);
        headers.set("X-User-Email", EMAIL_VALUE);
        headers.set("X-Referer-URL", SENSITIVE_URL_VALUE);
        headers.set("X-Personal-Data", PERSONAL_DATA_VALUE);

        restTemplate.exchange(
                "http://localhost:" + businessPort + "/api/v1/players?search=" + QUERY_VALUE
                        + "&email=" + EMAIL_VALUE,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        HttpHeaders payloadHeaders = new HttpHeaders();
        payloadHeaders.setContentType(MediaType.APPLICATION_JSON);
        payloadHeaders.set(HttpHeaders.AUTHORIZATION, AUTHORIZATION_VALUE);
        String payload = "{\"username\":\"" + PAYLOAD_VALUE + "\",\"password\":\""
                + TOKEN_VALUE + "\",\"email\":\"" + EMAIL_VALUE + "\",\"profileUrl\":\""
                + SENSITIVE_URL_VALUE + "\",\"personalData\":\"" + PERSONAL_DATA_VALUE + "\"}";
        restTemplate.exchange(
                "http://localhost:" + businessPort + "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(payload, payloadHeaders),
                String.class);

        restTemplate.exchange(
                "http://localhost:" + businessPort + "/api/v1/players/" + VARIABLE_PATH_VALUE,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
    }

    private ResponseEntity<String> get(int port, String path) {
        return restTemplate.getForEntity("http://localhost:" + port + path, String.class);
    }

    private void assertMetricsDoNotContainSensitiveValues(String metrics) {
        assertThat(metrics).isNotBlank();
        assertThat(metrics)
                .doesNotContain(
                        QUERY_VALUE,
                        AUTHORIZATION_VALUE,
                        API_KEY_VALUE,
                        COOKIE_VALUE,
                        TOKEN_VALUE,
                        PAYLOAD_VALUE,
                        VARIABLE_PATH_VALUE,
                        EMAIL_VALUE,
                        SENSITIVE_URL_VALUE,
                        PERSONAL_DATA_VALUE);
    }

    private void assertHttpDimensionsAreNotSensitiveOrHighCardinality(String metrics) {
        metrics.lines()
                .filter(line -> line.startsWith("http_server_requests_seconds"))
                .forEach(this::assertHttpSampleDimensions);
    }

    private void assertHttpSampleDimensions(String sample) {
        Matcher sampleMatcher = HTTP_SAMPLE_PATTERN.matcher(sample);
        assertThat(sampleMatcher.matches()).as("HTTP sample has a valid label block: %s", sample).isTrue();

        String labels = sampleMatcher.group("labels");
        Matcher labelMatcher = LABEL_PATTERN.matcher(labels);
        int consumed = 0;
        boolean hasUri = false;
        while (labelMatcher.find()) {
            assertThat(labelMatcher.start()).as("HTTP labels are syntactically contiguous: %s", sample)
                    .isEqualTo(consumed);

            String labelName = labelMatcher.group(1);
            String labelValue = labelMatcher.group(2);
            assertThat(ALLOWED_HTTP_TAGS).as("Unexpected HTTP label in sample: %s", sample)
                    .contains(labelName);
            if ("uri".equals(labelName)) {
                hasUri = true;
                assertNormalizedUri(labelValue, sample);
            }
            consumed = labelMatcher.end();
        }

        assertThat(labels.substring(consumed).trim())
                .as("HTTP labels contain only complete key/value pairs: %s", sample)
                .isEmpty();
        assertThat(hasUri).as("HTTP sample contains the normalized uri label: %s", sample).isTrue();
    }

    private void assertNormalizedUri(String uri, String sample) {
        assertThat(uri)
                .as("HTTP uri label is normalized: %s", sample)
                .doesNotContain("?", "#", "://")
                .doesNotContain(
                        QUERY_VALUE,
                        AUTHORIZATION_VALUE,
                        API_KEY_VALUE,
                        COOKIE_VALUE,
                        TOKEN_VALUE,
                        PAYLOAD_VALUE,
                        VARIABLE_PATH_VALUE,
                        EMAIL_VALUE,
                        SENSITIVE_URL_VALUE,
                        PERSONAL_DATA_VALUE)
                .doesNotMatch(".*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}.*")
                .doesNotMatch(".*/\\d+(?:/.*)?");
    }
}
