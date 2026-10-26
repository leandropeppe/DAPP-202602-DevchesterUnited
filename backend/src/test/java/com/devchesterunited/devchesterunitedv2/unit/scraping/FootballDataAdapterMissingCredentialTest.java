package com.devchesterunited.devchesterunitedv2.scraping.estadisticas;
import com.devchesterunited.devchesterunitedv2.scraping.catalogo.FootballDataException;

import com.devchesterunited.devchesterunitedv2.scraping.catalogo.FootballDataAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FootballDataAdapterMissingCredentialTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private HttpServer server;

    @AfterEach
    void detenerServidor() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void credencialAusenteImpideLaSincronizacionSinLlamarAlProveedorNiExponerSecretos() throws Exception {
        var llamadas = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            llamadas.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();

        String secretoNoExponible = "external-secret-never-log";
        var cliente = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .build();
        var adapter = new FootballDataAdapter(cliente, "   ", mapper,
                java.time.Clock.systemUTC(), duration -> { });

        assertThatThrownBy(adapter::sincronizar)
                .isInstanceOf(FootballDataException.class)
                .hasMessageNotContaining(secretoNoExponible)
                .hasMessageNotContaining("X-Auth-Token");
        assertThat(llamadas).hasValue(0);
    }
}
