package com.devchesterunited.devchesterunitedv2.adapter.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.devchesterunited.devchesterunitedv2.adapter.WebDriverFactory;
import com.devchesterunited.devchesterunitedv2.adapter.WhoScoredStatisticsConfiguration;
import com.devchesterunited.devchesterunitedv2.adapter.WhoScoredStatisticsConfiguration.CompetitionParameters;
import com.devchesterunited.devchesterunitedv2.adapter.WhoScoredStatisticsException;
import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.web.client.RestClient;

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
    void rechazaPaginaActualDistintaDeUno() {
        var setup = setup(invalidMetadata("\"currentPage\":2"), valid(), valid());

        assertThatThrownBy(() -> setup.adapter.obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(WhoScoredStatisticsException.class);
    }

    @Test
    void rechazaCantidadDeJugadoresDiferenteAlTotal() {
        var setup = setup(invalidMetadata("\"totalResults\":2"), valid(), valid());

        assertThatThrownBy(() -> setup.adapter.obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(WhoScoredStatisticsException.class);
    }

    @Test
    void rechazaTotalDeJugadoresMayorAlTamanoDePagina() {
        var setup = setup(invalidMetadata("\"totalResults\":351"), valid(), valid());

        assertThatThrownBy(() -> setup.adapter.obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(WhoScoredStatisticsException.class);
    }

    @Test
    void rechazaMetadataNumericaNegativa() {
        var setup = setup(invalidMetadata("\"resultsPerPage\":-1"), valid(), valid());

        assertThatThrownBy(() -> setup.adapter.obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(WhoScoredStatisticsException.class);
    }

    @Test
    void rechazaMetadataNula() {
        var setup = setup("{\"paging\":null,\"playerTableStats\":[]}",
                valid(), valid());

        assertThatThrownBy(() -> setup.adapter.obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(WhoScoredStatisticsException.class);
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
        configuration.setMinApp(true);
        configuration.setNumberOfPlayersToPick(350);
        var pair = new CompetitionParameters();
        pair.setTournamentOptions(2);
        pair.setStageId(25544);
        configuration.setCompetitions(Map.of("premier-league", pair));
        return new WhoScoredStatisticsAdapter(RestClient.builder(), "https://www.whoscored.com", "", 1_000_000,
                new ObjectMapper(), factory, configuration);
    }

    private String valid() {
        return "{\"paging\":{\"currentPage\":1,\"totalPages\":1,\"totalResults\":1,\"resultsPerPage\":350},"
                + "\"playerTableStats\":[{\"playerId\":101,\"name\":\"Ana Perez\",\"teamId\":201}]}";
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
            return response.replace("\"resultsPerPage\":350", replacement);
        }
        throw new IllegalArgumentException("Reemplazo no soportado en test: " + replacement);
    }

    private record TestSetup(WhoScoredStatisticsAdapter adapter, WebDriver driver) { }
}
