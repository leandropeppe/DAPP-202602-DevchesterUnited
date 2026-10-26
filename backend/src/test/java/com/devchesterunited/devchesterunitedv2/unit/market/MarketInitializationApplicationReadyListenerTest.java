package com.devchesterunited.devchesterunitedv2.unit.market;

import org.mockito.Mockito;

import com.devchesterunited.devchesterunitedv2.configuration.market.MarketInitializationApplicationReadyListener;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketInitializationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MarketInitializationApplicationReadyListenerTest {
    @Test
    void delegaLaInicializacionAlCoordinadorCuandoLaAplicacionEstaLista() {
        MarketInitializationService inicializacion = mock(
                MarketInitializationService.class);
        ApplicationReadyEvent evento = mock(ApplicationReadyEvent.class);
        var listener = new MarketInitializationApplicationReadyListener(inicializacion);

        listener.inicializarCuandoLaAplicacionEstaLista(evento);

        verify(inicializacion).initialize();
    }

    @Test
    void conservaElArranqueDeLaAplicacionSiLaInicializacionFalla() {
        MarketInitializationService inicializacion = mock(
                MarketInitializationService.class);
        ApplicationReadyEvent evento = mock(ApplicationReadyEvent.class);
        doThrow(new IllegalStateException("fallo de bootstrap"))
                .when(inicializacion).initialize();
        var listener = new MarketInitializationApplicationReadyListener(inicializacion);

        listener.inicializarCuandoLaAplicacionEstaLista(evento);

        verify(inicializacion).initialize();
    }
}
