package com.devchesterunited.devchesterunitedv2.integration.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import com.devchesterunited.devchesterunitedv2.configuration.auditoria.CorrelacionContext;
import com.devchesterunited.devchesterunitedv2.configuration.auditoria.CorrelacionHttpFilter;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationConcurrencyTest {
    @Test
    void sameExternalIdProducesIndependentInternalIdsWithoutMdcMixing() throws Exception {
        CorrelacionHttpFilter filter = new CorrelacionHttpFilter();
        Set<String> internalIds = ConcurrentHashMap.newKeySet();
        Set<String> seenExternalIds = ConcurrentHashMap.newKeySet();
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> runInvocation(filter, release, internalIds, seenExternalIds));
            var second = executor.submit(() -> runInvocation(filter, release, internalIds, seenExternalIds));
            release.countDown();
            first.get();
            second.get();
        } finally {
            executor.shutdownNow();
        }
        assertThat(internalIds).hasSize(2);
        assertThat(seenExternalIds).containsOnly("same-client-request");
    }

    private static void runInvocation(CorrelacionHttpFilter filter, CountDownLatch release,
            Set<String> internalIds, Set<String> seenExternalIds) {
        try {
            invoke(filter, release, internalIds, seenExternalIds);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private static void invoke(CorrelacionHttpFilter filter, CountDownLatch release,
            Set<String> internalIds, Set<String> seenExternalIds) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-ID", "same-client-request");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException(exception);
            }
            String internalId = MDC.get("requestCorrelationId");
            internalIds.add(internalId);
            seenExternalIds.add(MDC.get("externalCorrelationId"));
            assertThat(internalId).isEqualTo(response.getHeader("X-Correlation-ID"));
            assertThat(CorrelacionContext.current().orElseThrow().requestCorrelationId()).isEqualTo(internalId);
        });
        assertThat(MDC.get("requestCorrelationId")).isNull();
        assertThat(CorrelacionContext.current()).isEmpty();
    }
}
