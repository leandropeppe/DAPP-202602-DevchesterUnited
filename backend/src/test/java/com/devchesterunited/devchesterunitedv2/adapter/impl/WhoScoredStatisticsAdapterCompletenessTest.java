package com.devchesterunited.devchesterunitedv2.adapter.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.devchesterunited.devchesterunitedv2.adapter.WebDriverFactory;
import com.devchesterunited.devchesterunitedv2.adapter.WhoScoredStatisticsConfiguration;
import com.devchesterunited.devchesterunitedv2.adapter.WhoScoredStatisticsConfiguration.CompetitionParameters;
import com.devchesterunited.devchesterunitedv2.adapter.WhoScoredStatisticsException;
import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WhoScoredStatisticsAdapterCompletenessTest {
    @Test
    void aceptaTresRespuestasCompletasEnUnaPagina() {
        var setup = setup(valid(), valid(), valid());

        var snapshot = setup.adapter.obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE);

        assertThat(snapshot.registros()).hasSize(1);
        verify(setup.driver, times(3)).get(any(String.class));
    }

    @Test
    void rechazaMasDeUnaPagina() {
        var setup = setup(invalidMetadata("\"totalPages\":2"), valid(), valid());

        assertThatThrownBy(() -> setup.adapter.obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(WhoScoredStatisticsException.class);
        verify(setup.driver).quit();
    }

    @Test
    void informaContextoYCondicionesExactasCuandoLaRespuestaEstaPaginada() {
        String paginated = valid()
                .replace("\"totalPages\":1", "\"totalPages\":4")
                .replace("\"totalResults\":1", "\"totalResults\":2");
        var setup = setup(paginated, valid(), valid());

        var exception = assertThatThrownBy(
                () -> setup.adapter.obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(WhoScoredStatisticsException.class)
                .actual();

        assertThat(exception.getMessage())
                .startsWith("WhoScored devolvio una respuesta paginada o incompleta:")
                .contains("competicion=PREMIER_LEAGUE")
                .contains("familia=SUMMARY")
                .contains("category=summary")
                .contains("subcategory=all")
                .contains("currentPage=1")
                .contains("totalPages=4")
                .contains("resultsPerPage=500")
                .contains("totalResults=2")
                .contains("playersRecibidos=1")
                .contains("category=summary")
                .contains("subcategory=all")
                .contains("fallos=[totalPages != 1, playersRecibidos != totalResults]");
    }

    @ParameterizedTest
    @MethodSource("metadataInvalida")
    void rechazaMetadataInvalida(String respuesta) {
        var setup = setup(respuesta, valid(), valid());

        assertThatThrownBy(() -> setup.adapter.obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(WhoScoredStatisticsException.class);
    }

    private static Stream<Arguments> metadataInvalida() {
        return Stream.of(
                Arguments.of(invalidMetadataEstatica("\"currentPage\":2")),
                Arguments.of(invalidMetadataEstatica("\"totalResults\":2")),
                Arguments.of("{\"paging\":null,\"playerTableStats\":[]}"));
    }

    private static String invalidMetadataEstatica(String replacement) {
        String response = "{\"paging\":{\"currentPage\":1,\"totalPages\":1,"
                + "\"resultsPerPage\":500,\"totalResults\":1},"
                + "\"playerTableStats\":[{\"playerId\":101,\"name\":\"Ana Perez\",\"teamId\":201}]}";
        return replacement.startsWith("\"totalResults\"")
                ? response.replace("\"totalResults\":1", replacement)
                : response.replace("\"currentPage\":1", replacement);
    }

    @Test
    void acepta352JugadoresConPaginaDe400CuandoCoincideConTotalResults() {
        String response = responseWithPlayers(352, 352);
        var setup = setup(response, response, response);

        assertThat(setup.adapter.obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE).registros())
                .hasSize(352);
    }

    @Test
    void rechaza286JugadoresCuandoTotalResultsEs287() {
        String response = responseWithPlayers(287, 286);
        var setup = setup(response, response, response);

        assertThatThrownBy(() -> setup.adapter.obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(WhoScoredStatisticsException.class)
                .hasMessageContaining("playersRecibidos != totalResults");
    }

    @Test
    void rechazaTotalDeJugadoresMayorAlTamanoDePagina() {
        String response = responseWithPlayers(501, 500);
        var setup = setup(response, response, response);

        assertThatThrownBy(() -> setup.adapter.obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(WhoScoredStatisticsException.class)
                .hasMessageContaining("totalResults > resultsPerPage");
    }

    @Test
    void unaFamiliaIncompletaAbortaAntesDeCrearSnapshotParcial() {
        var setup = setup(valid(), invalidMetadata("\"players\":[]"), valid());

        assertThatThrownBy(() -> setup.adapter.obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(WhoScoredStatisticsException.class);
        verify(setup.driver, times(2)).get(any(String.class));
        verify(setup.driver).quit();
    }

    private TestSetup setup(String summary, String offensive, String defensive) {
        WebDriver driver = mock(WebDriver.class);
        WebElement body = mock(WebElement.class);
        WebDriverFactory factory = mock(WebDriverFactory.class);
        when(factory.create()).thenReturn(driver);
        when(driver.findElement(any(By.class))).thenReturn(body);
        when(body.getDomProperty("textContent")).thenReturn(summary, offensive, defensive);
        return new TestSetup(adapter(factory), driver);
    }

    private WhoScoredStatisticsAdapter adapter(WebDriverFactory factory) {
        var configuration = new WhoScoredStatisticsConfiguration();
        configuration.setEndpoint("/statisticsfeed/1/getplayerstatistics");
        configuration.setStatsAccumulationType(0);
        configuration.setCurrent(true);
        configuration.setSortBy("Rating");
        configuration.setField("Overall");
        configuration.setMinApp(false);
        configuration.setNumberOfPlayersToPick(500);
        var pair = new CompetitionParameters();
        pair.setTournamentOptions(2);
        pair.setStageId(25544);
        configuration.setCompetitions(Map.of("premier-league", pair));
        return new WhoScoredStatisticsAdapter("https://www.whoscored.com", new ObjectMapper(), factory, configuration);
    }

    private String valid() {
        return "{\"paging\":{\"currentPage\":1,\"totalPages\":1,\"totalResults\":1,\"resultsPerPage\":500},"
                + "\"playerTableStats\":[{\"playerId\":101,\"name\":\"Ana Perez\",\"teamId\":201}]}";
    }

    private String responseWithPlayers(int totalResults, int playersReceived) {
        StringBuilder response = new StringBuilder(
                "{\"paging\":{\"currentPage\":1,\"totalPages\":1,\"totalResults\":")
                .append(totalResults)
                .append(",\"resultsPerPage\":500},\"playerTableStats\":[");
        for (int index = 0; index < playersReceived; index++) {
            if (index > 0) {
                response.append(',');
            }
            response.append("{\"playerId\":").append(1000 + index)
                    .append(",\"name\":\"Jugador ").append(index)
                    .append("\",\"teamId\":201}");
        }
        return response.append("]}").toString();
    }

    private String invalidMetadata(String replacement) {
        String response = valid();
        if (replacement.startsWith("\"players\"")) {
            return response.replace("\"playerTableStats\":[{\"playerId\":101,\"name\":\"Ana Perez\",\"teamId\":201}]",
                    "\"playerTableStats\":[]");
        }
        if (replacement.startsWith("\"totalResults\"")) {
            return response.replace("\"totalResults\":1", replacement);
        }
        if (replacement.startsWith("\"currentPage\"")) {
            return response.replace("\"currentPage\":1", replacement);
        }
        if (replacement.startsWith("\"totalPages\"")) {
            return response.replace("\"totalPages\":1", replacement);
        }
        if (replacement.startsWith("\"resultsPerPage\"")) {
            return response.replace("\"resultsPerPage\":500", replacement);
        }
        throw new IllegalArgumentException("Reemplazo no soportado en test: " + replacement);
    }

    private record TestSetup(WhoScoredStatisticsAdapter adapter, WebDriver driver) { }
}
