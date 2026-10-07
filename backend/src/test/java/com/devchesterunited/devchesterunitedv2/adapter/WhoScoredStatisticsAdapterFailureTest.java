package com.devchesterunited.devchesterunitedv2.adapter;

import com.devchesterunited.devchesterunitedv2.adapter.impl.WhoScoredStatisticsAdapter;
import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.FamiliaEstadistica;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhoScoredStatisticsAdapterFailureTest {
    private HttpServer server;

    @AfterEach
    void detener() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void traduceCuotaSinFiltrarDetallesDelProveedor() throws Exception {
        iniciar(429, "{\"internalQuotaId\":\"secret\"}");

        assertThatThrownBy(() -> adapter().obtenerPagina(CompeticionEstadistica.SERIE_A,
                FamiliaEstadistica.GENERAL, 1, 10))
                .isInstanceOf(WhoScoredStatisticsException.class)
                .hasMessageContaining("cuota")
                .hasMessageNotContaining("secret");
    }

    @Test
    void traduceTimeoutComoFalloControlado() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/statistics", exchange -> {
            try {
                Thread.sleep(250);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();
        var factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(25);
        var cliente = new WhoScoredStatisticsAdapter(RestClient.builder()
                .requestFactory(factory), url(), "test", 10_000);

        assertThatThrownBy(() -> cliente.obtenerPagina(CompeticionEstadistica.SERIE_A,
                FamiliaEstadistica.GENERAL, 1, 10))
                .isInstanceOf(WhoScoredStatisticsException.class);
    }

    @Test
    void rechazaJsonInvalidoYMetadataIncompleta() throws Exception {
        iniciar(200, "not-json");
        assertThatThrownBy(() -> adapter().obtenerPagina(CompeticionEstadistica.SERIE_A,
                FamiliaEstadistica.GENERAL, 1, 10))
                .isInstanceOf(WhoScoredStatisticsException.class);

        server.stop(0);
        iniciar(200, "{\"page\":1,\"pageSize\":10,\"totalPages\":2,\"players\":[]}");
        assertThatThrownBy(() -> adapter().obtenerPagina(CompeticionEstadistica.SERIE_A,
                FamiliaEstadistica.GENERAL, 1, 10))
                .isInstanceOf(WhoScoredStatisticsException.class);
    }

    private WhoScoredStatisticsAdapter adapter() {
        return new WhoScoredStatisticsAdapter(RestClient.builder(), url(), "test", 10_000);
    }

    private void iniciar(int status, String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/statistics", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
