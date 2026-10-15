package com.devchesterunited.devchesterunitedv2.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.devchesterunited.devchesterunitedv2.configuration.MarketInitializationApplicationReadyListener;
import com.devchesterunited.devchesterunitedv2.service.interfaces.MarketInitializationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.context.event.ApplicationReadyEvent;

class MarketInitializationApplicationReadyListenerTest {
    @Test
    void delegaLaInicializacionAlCoordinadorCuandoLaAplicacionEstaLista() {
        MarketInitializationService inicializacion = Mockito.mock(
                MarketInitializationService.class);
        ApplicationReadyEvent evento = Mockito.mock(ApplicationReadyEvent.class);
        var listener = new MarketInitializationApplicationReadyListener(inicializacion);

        listener.inicializarCuandoLaAplicacionEstaLista(evento);

        verify(inicializacion).initialize();
    }

    @Test
    void conservaElArranqueDeLaAplicacionSiLaInicializacionFalla() {
        MarketInitializationService inicializacion = Mockito.mock(
                MarketInitializationService.class);
        ApplicationReadyEvent evento = Mockito.mock(ApplicationReadyEvent.class);
        doThrow(new IllegalStateException("fallo de bootstrap"))
                .when(inicializacion).initialize();
        var listener = new MarketInitializationApplicationReadyListener(inicializacion);

        listener.inicializarCuandoLaAplicacionEstaLista(evento);

        verify(inicializacion).initialize();
    }
}
