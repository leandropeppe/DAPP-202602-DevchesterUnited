package com.devchesterunited.devchesterunitedv2.unit.scraping;


import com.devchesterunited.devchesterunitedv2.modelo.jugador.Jugador;
import com.devchesterunited.devchesterunitedv2.scraping.catalogo.FootballDataAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FootballDataAdapterNullPositionTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private HttpServer server;

    @AfterEach
    void detenerServidor() {
        if (server != null) server.stop(0);
    }

    @Test
    void publicaSnapshotCuandoUnJugadorTienePosicionNula() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::responder);
        server.start();

        var cliente = RestClient.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build();
        var snapshot = new FootballDataAdapter(cliente, "test-key", mapper,
                java.time.Clock.systemUTC(), duration -> { }).sincronizar();

        assertThat(snapshot.equipos()).hasSize(96);
        assertThat(snapshot.jugadores()).anyMatch(j -> j.getPosicion() == null);
        assertThat(snapshot.jugadores()).extracting(Jugador::getNombre)
                .contains("Kepa Arrizabalaga", "Cristian Cásseres Jr.", "Pierre Emile Højbjerg");
    }

    private void responder(HttpExchange exchange) throws java.io.IOException {
        String codigo = exchange.getRequestURI().getPath().split("/")[2];
        List<String> codigos = List.of("PL", "PD", "BL1", "SA", "FL1");
        int liga = codigos.indexOf(codigo);
        int cantidad = codigo.equals("BL1") || codigo.equals("FL1") ? 18 : 20;
        var equipos = new ArrayList<Object>();
        for (int i = 0; i < cantidad; i++) {
            var jugador = new java.util.HashMap<String, Object>();
            jugador.put("id", 500000 + liga * 1000L + i);
            jugador.put("firstName", null);
            jugador.put("lastName", null);
            jugador.put("name", switch (i) {
                case 0 -> "Kepa Arrizabalaga";
                case 1 -> "Cristian Cásseres Jr.";
                case 2 -> "Pierre Emile Højbjerg";
                default -> "Jugador Prueba";
            });
            jugador.put("shirtNumber", 10);
            jugador.put("nationality", "Argentina");
            jugador.put("position", i == 0 ? null : "Midfield");
            var squad = List.of(jugador);
            equipos.add(java.util.Map.of("id", 600000 + liga * 1000L + i,
                    "name", "Equipo " + liga + " " + i, "shortName", "E", "tla", "E",
                    "crest", "https://img.example/crest.png", "squad", squad));
        }
        var body = mapper.writeValueAsBytes(java.util.Map.of("count", cantidad,
                "competition", java.util.Map.of("code", codigo),
                "season", java.util.Map.of("id", 2026, "startDate", "2026-08-01", "endDate", "2027-05-31"),
                "teams", equipos));
        exchange.getResponseHeaders().add("X-RequestsAvailable", "100");
        exchange.sendResponseHeaders(200, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
