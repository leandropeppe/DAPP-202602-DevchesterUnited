package com.devchesterunited.devchesterunitedv2.adapter;

import com.devchesterunited.devchesterunitedv2.adapter.impl.WhoScoredStatisticsAdapter;
import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.FamiliaEstadistica;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhoScoredStatisticsAdapterTest {
    private HttpServer server;

    @AfterEach
    void stop() { if (server != null) server.stop(0); }

    @Test
    void traduceFamiliaYMetadataDesdeServidorLocalControlado() throws Exception {
        iniciar(200, """
                {"page":2,"pageSize":2,"totalPages":3,"totalResults":5,"players":[
                 {"playerId":77,"name":"  Ana   Pérez ","position":"Midfielder","nationality":"Argentina",
                  "teamId":88,"teamName":" Equipo A ","minutesPlayed":90,"goals":2,"assists":1,
                  "shots":3.5,"keyPasses":4,"dribbles":5,"tackles":null,"rating":7.2,
                  "yellowCards":1,"redCards":0}]}""");

        var page = adapter().obtenerPagina(CompeticionEstadistica.PREMIER_LEAGUE,
                FamiliaEstadistica.OFENSIVA, 2, 2);

        assertThat(page.totalPaginas()).isEqualTo(3);
        assertThat(page.registros()).singleElement().satisfies(registro -> {
            assertThat(registro.identidad().playerId()).isEqualTo(77);
            assertThat(registro.identidad().nombre()).isEqualTo("Ana Pérez");
            assertThat(registro.familia()).isEqualTo(FamiliaEstadistica.OFENSIVA);
            assertThat(registro.metricas().tackles()).isNull();
        });
    }

    @Test
    void rechazaMetadataIncompatibleSinExponerDetallesHttp() throws Exception {
        iniciar(200, "{" + "\"page\":1,\"pageSize\":2,\"totalPages\":1,\"totalResults\":0,\"players\":[]}");
        assertThatThrownBy(() -> adapter().obtenerPagina(CompeticionEstadistica.LA_LIGA,
                FamiliaEstadistica.GENERAL, 2, 2))
                .isInstanceOf(WhoScoredStatisticsException.class);
    }

    @Test
    void traduceRespuestaHttpNoExitosa() throws Exception {
        iniciar(503, "{}");
        assertThatThrownBy(() -> adapter().obtenerPagina(CompeticionEstadistica.SERIE_A,
                FamiliaEstadistica.DEFENSIVA, 1, 2))
                .isInstanceOf(WhoScoredStatisticsException.class)
                .hasMessageContaining("no exitosa");
    }

    @Test
    void rechazaJsonInvalidoYCuerpoQueSuperaElLimite() throws Exception {
        iniciar(200, "{no-json}");
        assertThatThrownBy(() -> adapter().obtenerPagina(CompeticionEstadistica.SERIE_A,
                FamiliaEstadistica.GENERAL, 1, 2))
                .isInstanceOf(WhoScoredStatisticsException.class);

        server.stop(0);
        iniciar(200, "x".repeat(101));
        assertThatThrownBy(() -> new WhoScoredStatisticsAdapter(RestClient.builder(), baseUrl(), "test", 100)
                .obtenerPagina(CompeticionEstadistica.SERIE_A, FamiliaEstadistica.GENERAL, 1, 2))
                .isInstanceOf(WhoScoredStatisticsException.class)
                .hasMessageContaining("límite");
    }

    @Test
    void rechazaCamposObligatoriosValoresInvalidosYHintsInconsistentes() throws Exception {
        iniciar(200, "{\"page\":1,\"pageSize\":2,\"totalPages\":1,\"totalResults\":1,\"players\":["
                + "{\"playerId\":7,\"name\":\"Ana\",\"teamId\":8,\"teamName\":null,\"goals\":1}]}");
        assertThatThrownBy(() -> adapter().obtenerPagina(CompeticionEstadistica.SERIE_A,
                FamiliaEstadistica.GENERAL, 1, 2)).isInstanceOf(WhoScoredStatisticsException.class);

        server.stop(0);
        iniciar(200, "{\"page\":1,\"pageSize\":2,\"totalPages\":1,\"totalResults\":1,\"players\":["
                + "{\"playerId\":7,\"name\":\"Ana\",\"teamId\":8,\"teamName\":\"A\",\"goals\":-1}]}");
        assertThatThrownBy(() -> adapter().obtenerPagina(CompeticionEstadistica.SERIE_A,
                FamiliaEstadistica.GENERAL, 1, 2)).isInstanceOf(WhoScoredStatisticsException.class);
    }

    @Test
    void rechazaMetadataContradictoriaYPaginaFueraDeRango() throws Exception {
        iniciar(200, "{\"page\":1,\"pageSize\":2,\"totalPages\":1,\"totalResults\":0,\"players\":["
                + "{\"playerId\":7,\"name\":\"Ana\"}]}");
        assertThatThrownBy(() -> adapter().obtenerPagina(CompeticionEstadistica.SERIE_A,
                FamiliaEstadistica.GENERAL, 1, 2)).isInstanceOf(WhoScoredStatisticsException.class);

        server.stop(0);
        iniciar(200, "{\"page\":2,\"pageSize\":2,\"totalPages\":1,\"totalResults\":0,\"players\":[]}");
        assertThatThrownBy(() -> adapter().obtenerPagina(CompeticionEstadistica.SERIE_A,
                FamiliaEstadistica.GENERAL, 2, 2)).isInstanceOf(WhoScoredStatisticsException.class);
    }

    private WhoScoredStatisticsAdapter adapter() {
        return new WhoScoredStatisticsAdapter(RestClient.builder(), baseUrl(), "test", 1_000_000);
    }

    private void iniciar(int status, String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/statistics", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
    }

    private String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
}
