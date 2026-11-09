package com.devchesterunited.devchesterunitedv2.scraping.estadisticas;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.CompeticionEstadistica;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WhoScoredStatisticsAdapterCharacterizationTest {
    @Test
    void conservaEndpointAbsolutoSinAnteponerBaseUrl() {
        Fixture fixture = fixture("https://example.test///", "https://feed.test/statisticsfeed", false,
                response(player(101, "Ana Perez", 201L, null)));

        fixture.adapter().obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE);

        ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
        verify(fixture.driver(), times(3)).get(urls.capture());
        assertThat(urls.getAllValues()).allMatch(url -> url.startsWith("https://feed.test/statisticsfeed?"));
    }

    @Test
    void normalizaBarrasDelEndpointYQuitaBarrasFinalesDeBaseUrl() {
        Fixture fixture = fixture("https://example.test///", "///statisticsfeed", false,
                response(player(101, "Ana Perez", 201L, null)));

        fixture.adapter().obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE);

        ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
        verify(fixture.driver(), times(3)).get(urls.capture());
        assertThat(urls.getAllValues()).allSatisfy(url -> assertThat(url)
                .startsWith("https://example.test/statisticsfeed?")
                .contains("statsAccumulationType=0", "isCurrent=false", "stageId=25544",
                        "tournamentOptions=2", "category=summary", "subcategory="));
    }

    @Test
    void rechazaBaseUrlVaciaConEndpointRelativoAntesDeNavegar() {
        Fixture fixture = fixture("", "/statisticsfeed", false,
                response(player(101, "Ana Perez", 201L, null)));

        assertThatThrownBy(() -> fixture.adapter().obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(WhoScoredStatisticsException.class)
                .hasMessage("WhoScored no esta configurado.");

        verify(fixture.driver(), org.mockito.Mockito.never()).get(anyString());
        verify(fixture.driver()).quit();
    }

    @Test
    void conservaJugadoresConMismoNombreCuandoSusPlayerIdSonDistintos() {
        Fixture fixture = fixture(response(
                player(101, "Ana Perez", 201L, null),
                player(102, "Ana Perez", 202L, null)));

        var registros = fixture.adapter().obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE).registros();

        assertThat(registros).hasSize(2)
                .extracting(registro -> registro.identidad().playerId())
                .containsExactly(101L, 102L);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"playerId\":null,\"name\":\"Ana Perez\"",
            "\"playerId\":0,\"name\":\"Ana Perez\"",
            "\"playerId\":101,\"name\":null",
            "\"playerId\":101,\"name\":\"   \""
    })
    void rechazaIdentidadInsuficienteDelJugador(String identidad) {
        Fixture fixture = fixture(response(playerWithIdentity(identidad, 201L)));

        assertThatThrownBy(() -> fixture.adapter().obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(WhoScoredStatisticsException.class)
                .hasMessage("WhoScored no proporciono identidad suficiente del jugador.");
    }

    @Test
    void conservaTeamIdNuloComoParteDeLaRespuestaActual() {
        Fixture fixture = fixture(response(player(101, "Ana Perez", null, null)));

        var identidad = fixture.adapter().obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE)
                .registros().get(0).identidad();

        assertThat(identidad.teamId()).isNull();
    }

    @Test
    void conservaValorNoNuloCuandoFamiliaPosteriorEntregaNull() {
        Fixture fixture = fixture(
                response(playerWithRating(101, "Ana Perez", 201L, "7.20")),
                response(playerWithRating(101, "Ana Perez", 201L, null)),
                response(playerWithRating(101, "Ana Perez", 201L, null)));

        var metricas = fixture.adapter().obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE)
                .registros().get(0).metricas();

        assertThat(metricas.valoracion()).hasToString("7.20");
    }

    @Test
    void conservaCeroDeFamiliaCanonicaAunqueOtraFamiliaTengaOtroValor() {
        Fixture fixture = fixture(
                response(playerWithMetric(101, "Ana Perez", 201L, "keyPassPerGame", "9.90")),
                response(playerWithMetric(101, "Ana Perez", 201L, "keyPassPerGame", "0")),
                response(player(101, "Ana Perez", 201L, null)));

        var metricas = fixture.adapter().obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE)
                .registros().get(0).metricas();

        assertThat(metricas.pasesClave()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void omitePassingCuandoLaConfiguracionLoDeshabilita() {
        Fixture fixture = fixture(response(player(101, "Ana Perez", 201L, null)), false);

        fixture.adapter().obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE);

        verify(fixture.driver(), times(3)).get(anyString());
    }

    @Test
    void agregaExcepcionDeCierreCuandoElDriverFallaDespuesDeUnErrorPrincipal() {
        Fixture fixture = fixture(response(player(101, "Ana Perez", 201L, null)));
        doThrow(new WebDriverException("fallo al cerrar"))
                .when(fixture.driver()).quit();
        when(fixture.body().getDomProperty("textContent")).thenReturn("   ");

        Throwable error = org.assertj.core.api.Assertions.catchThrowable(
                () -> fixture.adapter().obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE));

        assertThat(error).isInstanceOf(WhoScoredStatisticsException.class)
                .hasMessage("WhoScored devolvio un body vacio.");
        assertThat(error.getSuppressed()).hasSize(1);
        assertThat(error.getSuppressed()[0])
                .isInstanceOf(WebDriverException.class);
    }

    @Test
    void rechazaColeccionNulaSinCrearSesion() {
        WebDriverFactory factory = mock(WebDriverFactory.class);
        WhoScoredStatisticsAdapter adapter = adapter(factory, "https://example.test", "/feed", true);

        assertThatThrownBy(() -> adapter.obtenerSnapshots(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Las competiciones son obligatorias.");

        verifyNoInteractions(factory);
    }

    @Test
    void traduceCompeticionNulaYSiempreCierraLaSesion() {
        Fixture fixture = fixture(response(player(101, "Ana Perez", 201L, null)));

        assertThatThrownBy(() -> fixture.adapter().obtenerSnapshot(null))
                .isInstanceOf(WhoScoredStatisticsException.class)
                .hasMessage("No se pudo adquirir statisticsfeed de WhoScored.");

        verify(fixture.driver()).quit();
    }

    private Fixture fixture(String response) {
        return fixture("https://example.test", "/statisticsfeed", false, response);
    }

    private Fixture fixture(String... responses) {
        return fixture("https://example.test", "/statisticsfeed", false, responses);
    }

    private Fixture fixture(String response, boolean passingEnabled) {
        return fixture("https://example.test", "/statisticsfeed", passingEnabled, response);
    }

    private Fixture fixture(String baseUrl, String endpoint, boolean passingEnabled, String... responses) {
        WebDriver driver = mock(WebDriver.class);
        WebElement body = mock(WebElement.class);
        WebDriverFactory factory = mock(WebDriverFactory.class);
        when(factory.create()).thenReturn(driver);
        when(driver.findElement(any(By.class))).thenReturn(body);
        int expectedResponses = passingEnabled ? 4 : 3;
        if (responses.length == 1) {
            String response = responses[0];
            responses = new String[expectedResponses];
            Arrays.fill(responses, response);
        }
        when(body.getDomProperty("textContent")).thenReturn(
                responses[0], Arrays.copyOfRange(responses, 1, responses.length));
        return new Fixture(adapter(factory, baseUrl, endpoint, passingEnabled), driver, body);
    }

    private WhoScoredStatisticsAdapter adapter(WebDriverFactory factory, String baseUrl,
            String endpoint, boolean passingEnabled) {
        var configuration = new WhoScoredStatisticsConfiguration();
        configuration.setEndpoint(endpoint);
        configuration.setPassingEnabled(passingEnabled);
        var pair = new WhoScoredStatisticsConfiguration.CompetitionParameters();
        pair.setTournamentOptions(2);
        pair.setStageId(25544);
        configuration.setCompetitions(Map.of("premier-league", pair));
        return new WhoScoredStatisticsAdapter(baseUrl, new ObjectMapper(), factory, configuration);
    }

    private String response(String... players) {
        return "{\"paging\":{\"currentPage\":1,\"totalPages\":1,\"totalResults\":"
                + players.length + ",\"resultsPerPage\":500},\"playerTableStats\":["
                + String.join(",", players) + "]}";
    }

    private String player(long id, String name, Long teamId, String ignored) {
        return playerWithIdentity("\"playerId\":" + id + ",\"name\":" + json(name), teamId);
    }

    private String playerWithIdentity(String identity, Long teamId) {
        return "{" + identity + ",\"teamId\":" + (teamId == null ? "null" : teamId)
                + "}";
    }

    private String playerWithRating(long id, String name, Long teamId, String rating) {
        return "{\"playerId\":" + id + ",\"name\":" + json(name) + ",\"teamId\":"
                + teamId + (rating == null ? "" : ",\"rating\":" + rating) + "}";
    }

    private String playerWithMetric(long id, String name, Long teamId, String metric, String value) {
        return "{\"playerId\":" + id + ",\"name\":" + json(name) + ",\"teamId\":"
                + teamId + ",\"" + metric + "\":" + value + "}";
    }

    private String json(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private record Fixture(WhoScoredStatisticsAdapter adapter, WebDriver driver, WebElement body) { }
}
