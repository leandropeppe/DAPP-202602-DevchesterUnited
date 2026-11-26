package com.devchesterunited.devchesterunitedv2.integration.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import com.devchesterunited.devchesterunitedv2.configuration.auditoria.CorrelacionContext;
import com.devchesterunited.devchesterunitedv2.configuration.auditoria.CorrelacionHttpFilter;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "management.server.address=0.0.0.0",
        "management.endpoints.web.exposure.include=health,prometheus",
        "management.endpoint.health.show-details=never",
        "token-market.initialization.auto-start=false"
})
class CorrelationMdcIntegrationTest {
    private final CorrelacionHttpFilter filter = new CorrelacionHttpFilter();

    @LocalServerPort
    private int businessPort;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private TestRestTemplate restTemplate;

    @AfterEach
    void cleanup() { CorrelacionContext.clear(); MDC.clear(); }

    @Test
    void exposesHttpContextDuringSuccessAndRemovesItAfterwards() throws Exception {
        AtomicReference<String> duringRequest = new AtomicReference<>();
        MockHttpServletResponse response = execute((request, result) -> {
            duringRequest.set(MDC.get("requestCorrelationId"));
            assertThat(MDC.get("contextType")).isEqualTo("HTTP");
        });
        assertThat(duringRequest).hasValue(response.getHeader("X-Correlation-ID"));
        assertThat(CorrelacionContext.current()).isEmpty();
        assertThat(MDC.get("contextType")).isNull();
    }

    @Test
    void removesContextWhenChainThrowsRuntimeException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
                throw new IllegalStateException("synthetic failure");
            });
        } catch (IllegalStateException expected) {
            // The filter must preserve the original chain failure.
        } catch (Exception unexpected) {
            throw new AssertionError(unexpected);
        }
        assertThat(CorrelacionContext.current()).isEmpty();
        assertThat(MDC.get("requestCorrelationId")).isNull();
    }

    @Test
    void preservesSecurityAndActuatorExposureWhileReturningCorrelationHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Correlation-ID", "invalid external value");
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> protectedResponse = restTemplate.exchange(
                "http://localhost:" + businessPort + "/api/v1/market/balance",
                HttpMethod.GET, request, new ParameterizedTypeReference<>() { });
        ResponseEntity<String> prometheusResponse = restTemplate.exchange(
                "http://localhost:" + managementPort + "/actuator/prometheus",
                HttpMethod.GET, request, new ParameterizedTypeReference<>() { });

        assertThat(protectedResponse.getStatusCode().value()).isEqualTo(401);
        assertThat(prometheusResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(protectedResponse.getHeaders().getFirst("X-Correlation-ID")).isNotBlank();
        assertThat(protectedResponse.getHeaders().getFirst("X-Correlation-ID"))
                .isNotEqualTo("invalid external value");
    }

    private MockHttpServletResponse execute(jakarta.servlet.FilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), response, chain);
        return response;
    }
}
