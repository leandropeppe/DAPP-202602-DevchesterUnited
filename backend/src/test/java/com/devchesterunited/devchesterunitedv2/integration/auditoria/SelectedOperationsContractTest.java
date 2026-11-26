package com.devchesterunited.devchesterunitedv2.integration.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaAspect;
import com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaOperationCatalog;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;
import org.aspectj.lang.annotation.Around;
import org.junit.jupiter.api.Test;

class SelectedOperationsContractTest {
    @Test
    void catalogHasExactlyTheTenSelectedOperations() {
        assertThat(AuditoriaOperationCatalog.operations()).containsExactlyInAnyOrder(
                "catalog.sync", "statistics.sync", "valuation.manual", "valuation.weekly",
                "market.purchase", "market.sale", "market.p2p.offer.publish",
                "market.p2p.purchase", "market.p2p.offer.cancel", "market.initialize");
    }

    @Test
    void aspectHasExactlyTenClosedAroundAdvices() {
        Set<String> pointcuts = java.util.Arrays.stream(AuditoriaAspect.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Around.class))
                .map(method -> method.getAnnotation(Around.class).value())
                .collect(Collectors.toSet());
        assertThat(pointcuts).hasSize(10);
        assertThat(pointcuts).noneMatch(pointcut -> pointcut.contains("MarketP2PPurchaseCommand"));
    }
}
