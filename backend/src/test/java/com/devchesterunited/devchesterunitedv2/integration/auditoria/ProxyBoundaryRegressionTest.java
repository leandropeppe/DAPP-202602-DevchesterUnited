package com.devchesterunited.devchesterunitedv2.integration.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaOperationCatalog;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProxyBoundaryRegressionTest {
    @Test
    void closedCatalogDoesNotExpandToInternalOrUnselectedBoundaries() {
        Set<String> operations = AuditoriaOperationCatalog.operations();
        assertThat(operations).containsExactlyInAnyOrder(
                "catalog.sync", "statistics.sync", "valuation.manual", "valuation.weekly",
                "market.purchase", "market.sale", "market.p2p.offer.publish",
                "market.p2p.purchase", "market.p2p.offer.cancel", "market.initialize");
        assertThat(operations).doesNotContain("market.p2p.purchase.command", "repository.find",
                "helper.internal", "self.invocation");
    }

    @Test
    void implementationDoesNotExposePrivateOrNonPublicAuditAdvice() {
        for (Method method : com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaAspect.class
                .getDeclaredMethods()) {
            if (method.isAnnotationPresent(org.aspectj.lang.annotation.Around.class)) {
                assertThat(Modifier.isPublic(method.getModifiers())).isTrue();
            }
        }
    }
}
