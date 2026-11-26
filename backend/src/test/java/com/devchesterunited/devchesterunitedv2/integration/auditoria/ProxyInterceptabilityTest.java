package com.devchesterunited.devchesterunitedv2.integration.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaAspect;
import com.devchesterunited.devchesterunitedv2.configuration.auditoria.AuditoriaEventWriter;
import com.devchesterunited.devchesterunitedv2.service.interfaces.estadisticas.SincronizacionCatalogoService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.estadisticas.SincronizacionEstadisticasJugadorService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketInitializationService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2PCancellationService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2POfferService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2PPurchaseService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketPurchaseService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketSaleService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.valorizacion.ValorizacionJugadorService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.aop.support.AopUtils;

class ProxyInterceptabilityTest {
    @Test
    void eachSelectedPublicInterfaceCanBeProxyWrapped() {
        AuditoriaAspect aspect = new AuditoriaAspect(mock(AuditoriaEventWriter.class));
        List<Object> proxies = List.of(
                proxy(aspect, SincronizacionCatalogoService.class),
                proxy(aspect, SincronizacionEstadisticasJugadorService.class),
                proxy(aspect, ValorizacionJugadorService.class),
                proxy(aspect, MarketPurchaseService.class),
                proxy(aspect, MarketSaleService.class),
                proxy(aspect, MarketP2POfferService.class),
                proxy(aspect, MarketP2PPurchaseService.class),
                proxy(aspect, MarketP2PCancellationService.class),
                proxy(aspect, MarketInitializationService.class));
        assertThat(proxies).allMatch(AopUtils::isAopProxy);
    }

    private static <T> T proxy(AuditoriaAspect aspect, Class<T> type) {
        AspectJProxyFactory factory = new AspectJProxyFactory(mock(type));
        factory.addAspect(aspect);
        return factory.getProxy();
    }
}
