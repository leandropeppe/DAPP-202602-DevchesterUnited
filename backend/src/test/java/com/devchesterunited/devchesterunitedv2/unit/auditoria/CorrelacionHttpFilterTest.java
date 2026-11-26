package com.devchesterunited.devchesterunitedv2.unit.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.devchesterunited.devchesterunitedv2.configuration.auditoria.CorrelacionContext;
import com.devchesterunited.devchesterunitedv2.configuration.auditoria.CorrelacionHttpFilter;
import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelacionHttpFilterTest {
    private final CorrelacionHttpFilter filter = new CorrelacionHttpFilter();

    @AfterEach
    void cleanup() {
        CorrelacionContext.clear();
        MDC.clear();
    }

    @Test
    void generatesInternalUuidAndReturnsItInResponse() throws Exception {
        MockHttpServletResponse response = execute(null, (request, result) -> {
            assertThat(MDC.get("requestCorrelationId")).isNotNull();
            assertThat(CorrelacionContext.current()).isPresent();
        });

        String correlationId = response.getHeader("X-Correlation-ID");
        assertThat(UUID.fromString(correlationId)).isNotNull();
        assertThat(response.getHeader("X-Correlation-ID")).isEqualTo(correlationId);
        assertThat(CorrelacionContext.current()).isEmpty();
        assertThat(MDC.get("requestCorrelationId")).isNull();
    }

    @Test
    void acceptsValidExternalValuesWithoutUsingThemAsInternalIdentity() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", "client.order-42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            assertThat(CorrelacionContext.current().orElseThrow().externalCorrelationId())
                    .isEqualTo("client.order-42");
            assertThat(MDC.get("externalCorrelationId")).isEqualTo("client.order-42");
            assertThat(MDC.get("requestCorrelationId")).isNotEqualTo("client.order-42");
        });
        assertThat(response.getHeader("X-Correlation-ID")).isNotEqualTo("client.order-42");
    }

    @Test
    void ignoresInvalidExternalValuesAndStillCleansUpAfterChainFailure() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", "invalid value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThrows(ServletException.class, () -> filter.doFilter(request, response,
                (FilterChain) (ignoredRequest, ignoredResponse) -> { throw new ServletException("failure"); }));
        assertThat(CorrelacionContext.current()).isEmpty();
        assertThat(MDC.get("requestCorrelationId")).isNull();
        assertThat(MDC.get("externalCorrelationId")).isNull();
    }

    private MockHttpServletResponse execute(String externalId, FilterChain chain) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (externalId != null) {
            request.addHeader("X-Correlation-ID", externalId);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }
}
