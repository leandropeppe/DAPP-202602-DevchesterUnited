package com.devchesterunited.devchesterunitedv2.adapter;

import com.devchesterunited.devchesterunitedv2.adapter.impl.FootballDataAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;

class FootballDataAdapterTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private HttpServer server;

    @AfterEach
    void detenerServidor() {
        if (server != null) server.stop(0);
    }


    @Test
    void consultaLasCincoLigasEnServidorLocalYMapeaElSnapshot() throws Exception {
        var rutas = new ArrayList<String>();
        iniciar((exchange, codigo) -> {
            rutas.add(exchange.getRequestURI().getPath());
            responder(exchange, 200, payload(codigo, cantidad(codigo), false), "100");
        });

        var snapshot = adapter().sincronizar();

        assertThat(snapshot.getEquipos()).hasSize(96);
        assertThat(snapshot.getJugadores()).hasSize(96);
        assertThat(rutas).containsExactly("/competitions/PL/teams", "/competitions/PD/teams",
                "/competitions/BL1/teams", "/competitions/SA/teams", "/competitions/FL1/teams");
        assertThat(snapshot.getJugadores().get(0).getIdExterno()).isNotNull();
        assertThat(snapshot.getEquipos().get(0).getEscudoUrl()).isEqualTo(URI.create("https://img.example/10000.png"));
    }

    @Test
    void mapeaPosicionesYAdmiteDorsalNulo() throws Exception {
        iniciar((exchange, codigo) -> responder(exchange, 200, payload(codigo, cantidad(codigo), true), "100"));
        var jugadores = adapter().sincronizar().getJugadores();
        assertThat(jugadores).extracting(j -> j.getPosicion()).contains("Defender", "Midfielder", "Goalkeeper", "Forward");
        assertThat(jugadores).anyMatch(j -> j.getDorsal() == 0 && j.getNombre().equals("Solo Nombre"));
    }

    private void assertSincronizacionFalla() {
        FootballDataAdapter adaptador = adapter();

        assertThatThrownBy(adaptador::sincronizar)
                .isInstanceOf(FootballDataException.class);
    }

    @Test
    void traduce4295xxPayloadInvalidoYCuotaAgotada() throws Exception {
        iniciar((exchange, codigo) -> responder(exchange, 429, "{}", "100"));
        assertSincronizacionFalla();
        detenerServidor();

        iniciar((exchange, codigo) -> responder(exchange, 200, payload(codigo, 1, false), "100"));
        assertSincronizacionFalla();
        detenerServidor();

        iniciar((exchange, codigo) -> responder(exchange, 200, payload(codigo, cantidad(codigo), false), "0"));
        assertSincronizacionFalla();
        detenerServidor();

        iniciar((exchange, codigo) -> responder(exchange, 503, "{}", "100"));
        assertSincronizacionFalla();
    }

    @Test
    void traduceTimeoutSinAccederAInternet() throws Exception {
        var liberarRespuesta = new CountDownLatch(1);

        iniciar((exchange, codigo) -> {
            try {
                if (!liberarRespuesta.await(2, TimeUnit.SECONDS)) {
                    throw new IOException("El test no libero la respuesta HTTP.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("El handler HTTP fue interrumpido.", e);
            }
            responder(exchange, 200, payload(codigo, cantidad(codigo), false), "100");
        });

        var clienteHttp = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(100))
                .build();

        var factory = new JdkClientHttpRequestFactory(clienteHttp);
        factory.setReadTimeout(Duration.ofMillis(50));

        var cliente = RestClient.builder()
                .baseUrl(baseUrl())
                .requestFactory(factory)
                .build();

        var clock = java.time.Clock.systemUTC();

        var adaptador = new FootballDataAdapter(
                cliente,
                "test-key",
                mapper,
                clock,
                d -> { }
        );

        try {
            assertThatThrownBy(adaptador::sincronizar)
                    .isInstanceOf(FootballDataException.class);
        } finally {
            liberarRespuesta.countDown();
        }
    }

    private FootballDataAdapter adapter() {
        var cliente = RestClient.builder().baseUrl(baseUrl()).build();
        return new FootballDataAdapter(cliente, "test-key", mapper, java.time.Clock.systemUTC(), d -> { });
    }

    private void iniciar(Handler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String[] partes = exchange.getRequestURI().getPath().split("/");
            handler.handle(exchange, partes.length > 2 ? partes[2] : "");
        });
        server.start();
    }

    private String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

    private void responder(HttpExchange exchange, int status, String body, String cuota) throws IOException {
        exchange.getResponseHeaders().add("X-RequestsAvailable", cuota);
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) { out.write(bytes); }
    }

    private String payload(String codigo, int cantidad, boolean variantes) throws IOException {
        var ligas = List.of("PL", "PD", "BL1", "SA", "FL1");
        int indice = ligas.indexOf(codigo);
        var equipos = new ArrayList<Object>();
        for (int i = 0; i < cantidad; i++) {
            int id = 10000 + indice * 1000 + i;
            var jugadores = new ArrayList<Object>();
            String posicion = variantes ? List.of("Defence", "Midfield", "Goalkeeper", "Forward").get(i % 4) : "Forward";
            var jugador = new java.util.HashMap<String, Object>();
            jugador.put("id", id + 50000);
            jugador.put("firstName", variantes && i % 4 == 0 ? null : "N");
            jugador.put("lastName", variantes && i % 4 == 0 ? null : "A");
            jugador.put("name", variantes && i % 4 == 0 ? "Solo Nombre" : "Nombre A");
            jugador.put("shirtNumber", variantes && i % 4 == 0 ? null : (i % 99) + 1);
            jugador.put("nationality", "Argentina");
            jugador.put("position", posicion);
            jugadores.add(jugador);
            var equipo = java.util.Map.of("id", id, "name", "Equipo " + id, "shortName", "E",
                    "tla", "E" + (i % 10), "crest", "https://img.example/" + id + ".png", "squad", jugadores);
            equipos.add(equipo);
        }
        return mapper.writeValueAsString(java.util.Map.of("count", cantidad,
                "competition", java.util.Map.of("code", codigo),
                "season", java.util.Map.of("id", 2026, "startDate", "2026-08-01", "endDate", "2027-05-31"),
                "teams", equipos));
    }

    private static int cantidad(String codigo) { return codigo.equals("BL1") || codigo.equals("FL1") ? 18 : 20; }
    @FunctionalInterface private interface Handler { void handle(HttpExchange exchange, String codigo) throws IOException; }
}
