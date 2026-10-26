package com.devchesterunited.devchesterunitedv2.scraping.estadisticas;

import com.devchesterunited.devchesterunitedv2.modelo.equipo.Equipo;
import com.devchesterunited.devchesterunitedv2.scraping.estadisticas.WebDriverFactory;
import com.devchesterunited.devchesterunitedv2.scraping.estadisticas.WhoScoredStatisticsAdapter;
import com.devchesterunited.devchesterunitedv2.scraping.estadisticas.WhoScoredStatisticsConfiguration;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.scraping.estadisticas.WhoScoredStatisticsConfiguration.CompetitionParameters;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

class WhoScoredStatisticsAdapterSnapshotTest {
    @Test
    void adquiereLasTresFamiliasConUnaSesionYConsolidaConPrecedenciaCanonica() {
        WebDriver driver = mock(WebDriver.class);
        WebElement body = mock(WebElement.class);
        WebDriverFactory factory = mock(WebDriverFactory.class);
        when(factory.create()).thenReturn(driver);
        when(driver.findElement(any(By.class))).thenReturn(body);
        when(body.getDomProperty("textContent")).thenReturn(summary(), offensive(), defensive());

        var adapter = adapter(factory);
        var snapshot = adapter.obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE);

        assertThat(snapshot.registros()).singleElement().satisfies(registro -> {
            assertThat(registro.identidad().playerId()).isEqualTo(101);
            assertThat(registro.identidad().teamId()).isEqualTo(201);
            assertThat(registro.identidad().posicion()).isNull();
            assertThat(registro.metricas().minutosJugados()).isEqualTo(900);
            assertThat(registro.metricas().goles()).isEqualTo(8);
            assertThat(registro.metricas().valoracion()).hasToString("7.20");
            assertThat(registro.metricas().pasesClave()).hasToString("1.70");
            assertThat(registro.metricas().regates()).hasToString("2.10");
            assertThat(registro.metricas().tackles()).hasToString("1.90");
        });
        verify(factory, times(1)).create();
        verify(driver, times(3)).get(any(String.class));
        verify(driver).quit();
    }

    @Test
    void construyeLasTresConsultasConLaConfiguracionInterna() {
        WebDriver driver = mock(WebDriver.class);
        WebElement body = mock(WebElement.class);
        WebDriverFactory factory = mock(WebDriverFactory.class);
        when(factory.create()).thenReturn(driver);
        when(driver.findElement(any(By.class))).thenReturn(body);
        when(body.getDomProperty("textContent")).thenReturn(emptyResponse(), emptyResponse(), emptyResponse());

        adapter(factory).obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE);

        var urls = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(driver, times(3)).get(urls.capture());
        assertThat(urls.getAllValues()).allSatisfy(url -> {
            assertThat(url).contains("statsAccumulationType=0", "isCurrent=true",
                    "stageId=25544", "tournamentOptions=2", "sortBy=Rating",
                    "field=Overall", "isMinApp=false", "numberOfPlayersToPick=500");
        });
        assertThat(urls.getAllValues().get(0)).contains("category=summary", "subcategory=all");
        assertThat(urls.getAllValues().get(1)).contains("category=summary", "subcategory=offensive");
        assertThat(urls.getAllValues().get(2)).contains("category=summary", "subcategory=defensive");
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

    private String summary() {
        return """
                {"paging":{"currentPage":1,"totalPages":1,"totalResults":1,"resultsPerPage":500},
                 "playerTableStats":[{"playerId":101,"name":" Ana   Perez ","teamId":201,"teamName":" Equipo A ",
                 "minsPlayed":900,"goal":8,"assistTotal":4,"shotsPerGame":2.4,"keyPassPerGame":9.9,
                 "dribbleWonPerGame":9.9,"tacklePerGame":9.9,"rating":7.2,"yellowCard":1,"redCard":0}]}
                """;
    }

    private String offensive() {
        return """
                {"paging":{"currentPage":1,"totalPages":1,"totalResults":1,"resultsPerPage":500},
                 "playerTableStats":[{"playerId":101,"name":"Ana Perez","teamId":201,"teamName":"Equipo A",
                 "minsPlayed":999,"goal":99,"assistTotal":99,"shotsPerGame":9.9,"keyPassPerGame":1.7,
                 "dribbleWonPerGame":2.1,"rating":9.9}]}
                """;
    }

    private String defensive() {
        return """
                {"paging":{"currentPage":1,"totalPages":1,"totalResults":1,"resultsPerPage":500},
                 "playerTableStats":[{"playerId":101,"name":"Ana Perez","teamId":201,"teamName":"Equipo A",
                 "minsPlayed":998,"tacklePerGame":1.9,"rating":9.8}]}
                """;
    }

    private String emptyResponse() {
        return "{\"paging\":{\"currentPage\":1,\"totalPages\":1,\"totalResults\":0,\"resultsPerPage\":500},\"playerTableStats\":[]}";
    }
}
