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

    @Test
    void agrupaPorPlayerIdAunqueAparezcaIntercaladoYRepetidoEnUnaFamilia() {
        Fixture fixture = fixture(
                response(player(102, "Jugador Dos", 202L, null),
                        player(101, "Jugador Uno", 201L, null),
                        player(102, "Jugador Dos", 202L, null)),
                response(player(103, "Jugador Tres", 203L, null),
                        player(101, "Jugador Uno", 201L, null)),
                response(player(102, "Jugador Dos", 202L, null),
                        player(103, "Jugador Tres", 203L, null)));

        var registros = fixture.adapter().obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE)
                .registros();

        assertThat(registros).hasSize(3)
                .extracting(registro -> registro.identidad().playerId())
                .containsExactly(102L, 101L, 103L);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"playerId\":null,\"name\":\"Ana Perez\"",
            "\"playerId\":0,\"name\":\"Ana Perez\"",
            "\"playerId\":-1,\"name\":\"Ana Perez\"",
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
    void conservaElPrimerValorNoNuloIncluidoElCero() {
        Fixture fixture = fixture(
                response(playerWithFields(101, "\"goal\":0,\"rating\":0.0"),
                        playerWithFields(101, "\"goal\":5,\"rating\":9.0")),
                response(playerWithFields(101, "\"goal\":8,\"rating\":8.0")),
                response(playerWithFields(101, "\"goal\":7,\"rating\":7.0")));

        var metricas = fixture.adapter().obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE)
                .registros().get(0).metricas();

        assertThat(metricas.goles()).isZero();
        assertThat(metricas.valoracion()).isEqualByComparingTo(BigDecimal.ZERO);
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
    void aplicaPrioridadesEspecificasDeOfensivaYDefensa() {
        Fixture fixture = fixture(
                response(playerWithFields(101, "\"keyPassPerGame\":9.9,"
                        + "\"dribbleWonPerGame\":9.8,\"tacklePerGame\":9.7")),
                response(playerWithFields(101, "\"keyPassPerGame\":1.1,"
                        + "\"dribbleWonPerGame\":2.2")),
                response(playerWithFields(101, "\"tacklePerGame\":3.3")));

        var metricas = fixture.adapter().obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE)
                .registros().get(0).metricas();

        assertThat(metricas.pasesClave()).isEqualByComparingTo(new BigDecimal("1.10"));
        assertThat(metricas.regates()).isEqualByComparingTo(new BigDecimal("2.20"));
        assertThat(metricas.tackles()).isEqualByComparingTo(new BigDecimal("3.30"));
    }

    @Test
    void normalizaNombreYEquipoYConservaElPrimerTeamIdNoNulo() {
        Fixture fixture = fixture(
                response(playerWithDetails(101, "  Ana   Perez  ", null, "   ")),
                response(playerWithDetails(101, "Otro Nombre", 201L, "  Equipo   Unido  ")),
                response(playerWithDetails(101, "Tercer Nombre", 202L, "Otro Equipo")));

        var identidad = fixture.adapter().obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE)
                .registros().get(0).identidad();

        assertThat(identidad.nombre()).isEqualTo("Ana Perez");
        assertThat(identidad.teamId()).isEqualTo(201L);
        assertThat(identidad.nombreEquipo()).isEqualTo("Equipo Unido");
    }

    @Test
    void construyeLasCuatroFamiliasDeMetricasConValoresRepresentativos() {
        Fixture fixture = fixture(true,
                response(playerWithFields(101, "\"apps\":12,\"subOn\":2,\"minsPlayed\":900,"
                        + "\"manOfTheMatch\":1,\"aerialWonPerGame\":1.2,\"rating\":7.4,"
                        + "\"yellowCard\":1,\"redCard\":0")),
                response(playerWithFields(101, "\"goal\":8,\"assistTotal\":4,\"shotsPerGame\":2.1,"
                        + "\"keyPassPerGame\":1.7,\"dribbleWonPerGame\":2.1,"
                        + "\"foulGivenPerGame\":0.3,\"offsideGivenPerGame\":0.2,"
                        + "\"dispossessedPerGame\":0.4,\"turnoverPerGame\":0.5")),
                response(playerWithFields(101, "\"tacklePerGame\":1.9,\"interceptionPerGame\":1.1,"
                        + "\"foulsPerGame\":0.2,\"offsideWonPerGame\":0.3,\"clearancePerGame\":4.4,"
                        + "\"wasDribbledPerGame\":0.5,\"outfielderBlockPerGame\":0.6,\"goalOwn\":0")),
                response(playerWithFields(101, "\"totalPassesPerGame\":44.2,\"passSuccess\":86.44,"
                        + "\"accurateCrossesPerGame\":1.2,\"accurateLongPassPerGame\":2.3,"
                        + "\"accurateThroughBallPerGame\":0.4")));

        var metricas = fixture.adapter().obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE)
                .registros().get(0).metricas();

        assertThat(metricas.generales().apps()).isEqualTo(12);
        assertThat(metricas.ofensivas().goal()).isEqualTo(8);
        assertThat(metricas.defensivas().tacklePerGame()).hasToString("1.90");
        assertThat(metricas.pases().passSuccess()).hasToString("86.44");
    }

    @Test
    void omitePassingCuandoLaConfiguracionLoDeshabilita() {
        Fixture fixture = fixture(response(player(101, "Ana Perez", 201L, null)), false);

        fixture.adapter().obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE);

        verify(fixture.driver(), times(3)).get(anyString());
    }

    @Test
    void noInventaMetricasDePasesCuandoPassingEstaDeshabilitado() {
        Fixture fixture = fixture(response(player(101, "Ana Perez", 201L, null)), false);

        var pases = fixture.adapter().obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE)
                .registros().get(0).metricas().pases();

        assertThat(pases.totalPassesPerGame()).isNull();
        assertThat(pases.passSuccess()).isNull();
        assertThat(pases.accurateCrossesPerGame()).isNull();
        assertThat(pases.accurateLongPassPerGame()).isNull();
        assertThat(pases.accurateThroughBallPerGame()).isNull();
    }

    @Test
    void propagaLaExcepcionExistenteDeMetricasInvalidas() {
        Fixture fixture = fixture(response(playerWithFields(101, "\"goal\":-1")));

        assertThatThrownBy(() -> fixture.adapter().obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(WhoScoredStatisticsException.class)
                .hasMessage("No se pudo adquirir statisticsfeed de WhoScored.")
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("La métrica goal no puede ser negativa.");
    }

    @Test
    void rechazaJugadorNuloConLaExcepcionYMensajeExistentes() {
        Fixture fixture = fixture(response("null"));

        assertThatThrownBy(() -> fixture.adapter().obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(WhoScoredStatisticsException.class)
                .hasMessage("WhoScored no proporciono identidad suficiente del jugador.");
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

    private Fixture fixture(boolean passingEnabled, String... responses) {
        return fixture("https://example.test", "/statisticsfeed", passingEnabled, responses);
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

    private String playerWithFields(long id, String... fields) {
        return "{\"playerId\":" + id + ",\"name\":\"Ana Perez\",\"teamId\":201"
                + (fields.length == 0 ? "" : "," + String.join(",", fields)) + "}";
    }

    private String playerWithDetails(long id, String name, Long teamId, String teamName) {
        return "{\"playerId\":" + id + ",\"name\":" + json(name) + ",\"teamId\":"
                + (teamId == null ? "null" : teamId) + ",\"teamName\":" + json(teamName) + "}";
    }

    private String json(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private record Fixture(WhoScoredStatisticsAdapter adapter, WebDriver driver, WebElement body) { }
}
