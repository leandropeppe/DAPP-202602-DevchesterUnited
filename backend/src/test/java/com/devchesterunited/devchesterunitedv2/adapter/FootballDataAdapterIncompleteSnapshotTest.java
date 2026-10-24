package com.devchesterunited.devchesterunitedv2.adapter;
import com.devchesterunited.devchesterunitedv2.modelo.equipo.Equipo;
import com.devchesterunited.devchesterunitedv2.scraping.catalogo.FootballDataException;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.Jugador;

import com.devchesterunited.devchesterunitedv2.scraping.catalogo.FootballDataAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class FootballDataAdapterIncompleteSnapshotTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private HttpServer server;

    @AfterEach
    void detenerServidor() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void respuestaParcialDeUnaLigaNoFormaUnSnapshotValido() throws Exception {
        var llamadas = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            int llamada = llamadas.incrementAndGet();
            String codigo = exchange.getRequestURI().getPath().split("/")[2];
            responder(exchange, snapshot(codigo, llamada == 5));
        });
        server.start();

        var cliente = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .build();
        var adapter = new FootballDataAdapter(cliente, "synthetic-provider-key", mapper,
                java.time.Clock.systemUTC(), duration -> { });

        assertThatThrownBy(adapter::sincronizar)
                .isInstanceOf(FootballDataException.class)
                .hasMessage("La respuesta del proveedor no forma un snapshot utilizable.");
        assertThat(llamadas).hasValue(5);
    }

    private void responder(HttpExchange exchange, String body) throws IOException {
        exchange.getResponseHeaders().add("X-RequestsAvailable", "100");
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private String snapshot(String codigo, boolean incompleto) throws IOException {
        List<String> ligas = List.of("PL", "PD", "BL1", "SA", "FL1");
        int indiceLiga = ligas.indexOf(codigo);
        int cantidad = codigo.equals("BL1") || codigo.equals("FL1") ? 18 : 20;
        int cantidadRespuesta = incompleto ? cantidad - 1 : cantidad;
        var equipos = new ArrayList<Object>();
        for (int i = 0; i < cantidadRespuesta; i++) {
            int idEquipo = 10000 + indiceLiga * 1000 + i;
            var jugador = java.util.Map.<String, Object>of(
                    "id", idEquipo + 50000,
                    "name", "Jugador " + idEquipo,
                    "shirtNumber", 9,
                    "nationality", "Argentina",
                    "position", "Forward");
            equipos.add(java.util.Map.of(
                    "id", idEquipo,
                    "name", "Equipo " + idEquipo,
                    "shortName", "E",
                    "tla", "E",
                    "crest", "https://img.example/" + idEquipo + ".png",
                    "squad", List.of(jugador)));
        }
        return mapper.writeValueAsString(java.util.Map.of(
                "count", cantidad,
                "competition", java.util.Map.of("code", codigo),
                "season", java.util.Map.of("id", 2026, "startDate", "2026-08-01", "endDate", "2027-05-31"),
                "teams", equipos));
    }
}
