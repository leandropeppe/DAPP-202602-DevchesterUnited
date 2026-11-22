package com.devchesterunited.devchesterunitedv2.scraping.catalogo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class FootballDataAdapterLifecycleTest {

    @Test
    void cierraUnaSolaVezElClienteQueLePertenece() {
        var clienteHttp = mock(HttpClient.class);
        var adapter = adapterConClientePropio(clienteHttp);

        adapter.cerrarClientePropio();
        adapter.cerrarClientePropio();

        verify(clienteHttp).close();
    }

    @Test
    void noCierraElClienteExterno() {
        var clienteHttp = mock(HttpClient.class);
        var clienteExterno = crearClienteExterno(clienteHttp);
        var adapter = new FootballDataAdapter(clienteExterno, "test-key", new ObjectMapper(),
                Clock.systemUTC(), duration -> { });

        adapter.cerrarClientePropio();

        verify(clienteHttp, never()).close();
    }

    @Test
    void springEjecutaPreDestroyYSoloCierraElClientePropio() {
        var clientePropio = mock(HttpClient.class);
        var clienteExterno = mock(HttpClient.class);
        var adapterPropietario = adapterConClientePropio(clientePropio);
        var adapterExterno = new FootballDataAdapter(crearClienteExterno(clienteExterno), "test-key",
                new ObjectMapper(), Clock.systemUTC(), duration -> { });

        try (var contexto = new AnnotationConfigApplicationContext()) {
            contexto.registerBean("adapterPropietario", FootballDataAdapter.class,
                    () -> adapterPropietario);
            contexto.registerBean("adapterExterno", FootballDataAdapter.class,
                    () -> adapterExterno);
            contexto.refresh();
        }

        verify(clientePropio, times(1)).close();
        verify(clienteExterno, never()).close();
    }

    private FootballDataAdapter adapterConClientePropio(HttpClient clienteHttp) {
        return new FootballDataAdapter(RestClient.create(), "test-key", new ObjectMapper(),
                Clock.systemUTC(), duration -> { }, clienteHttp);
    }

    private RestClient crearClienteExterno(HttpClient clienteHttp) {
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(clienteHttp))
                .build();
    }
}
