package com.devchesterunited.devchesterunitedv2.scraping.estadisticas;

import com.devchesterunited.devchesterunitedv2.scraping.estadisticas.WebDriverFactory;
import com.devchesterunited.devchesterunitedv2.scraping.estadisticas.WhoScoredStatisticsAdapter;
import com.devchesterunited.devchesterunitedv2.scraping.estadisticas.WhoScoredStatisticsConfiguration;
import com.devchesterunited.devchesterunitedv2.scraping.estadisticas.WhoScoredStatisticsException;
import static org.mockito.Mockito.doAnswer;
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
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

class WhoScoredStatisticsAdapterMultiCompetitionTest {
    @Test
    void usaUnaSesionParaLasCincoCompeticionesYLaCierraUnaVez() {
        WebDriver driver = mock(WebDriver.class);
        WebElement body = mock(WebElement.class);
        WebDriverFactory factory = mock(WebDriverFactory.class);
        when(factory.create()).thenReturn(driver);
        when(driver.findElement(any(By.class))).thenReturn(body);
        var responses = emptyResponses(15);
        when(body.getDomProperty("textContent")).thenReturn(responses[0], java.util.Arrays.copyOfRange(responses, 1, responses.length));

        var snapshots = adapter(factory).obtenerSnapshots(List.of(CompeticionEstadistica.values()));

        assertThat(snapshots).hasSize(5)
                .extracting(snapshot -> snapshot.competicion())
                .containsExactly(CompeticionEstadistica.values());
        verify(factory).create();
        verify(driver, times(15)).get(any(String.class));
        verify(driver).quit();
    }

    @Test
    void cierraLaSesionYNoNavegaCompeticionesPosterioresTrasUnFallo() {
        WebDriver driver = mock(WebDriver.class);
        WebElement body = mock(WebElement.class);
        WebDriverFactory factory = mock(WebDriverFactory.class);
        when(factory.create()).thenReturn(driver);
        when(driver.findElement(any(By.class))).thenReturn(body);
        var responses = emptyResponses(3);
        when(body.getDomProperty("textContent")).thenReturn(responses[0], java.util.Arrays.copyOfRange(responses, 1, responses.length));
        AtomicInteger navigations = new AtomicInteger();
        doAnswer(invocation -> {
            if (navigations.incrementAndGet() == 4) {
                throw new WebDriverException("controlled navigation failure");
            }
            return null;
        }).when(driver).get(any(String.class));

        var adapter = adapter(factory);
        var competitions = List.of(CompeticionEstadistica.values());
        assertThatThrownBy(() -> adapter.obtenerSnapshots(competitions))
                .isInstanceOf(WhoScoredStatisticsException.class);

        verify(factory).create();
        verify(driver, times(4)).get(any(String.class));
        verify(driver).quit();
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
        Map<String, CompetitionParameters> competitions = new java.util.HashMap<>();
        int[] tournamentOptions = {2, 4, 5, 3, 22};
        int[] stageIds = {25544, 25662, 25518, 25666, 25554};
        for (int index = 0; index < CompeticionEstadistica.values().length; index++) {
            var pair = new CompetitionParameters();
            pair.setTournamentOptions(tournamentOptions[index]);
            pair.setStageId(stageIds[index]);
            competitions.put(CompeticionEstadistica.values()[index].name().toLowerCase().replace('_', '-'), pair);
        }
        configuration.setCompetitions(competitions);
        return new WhoScoredStatisticsAdapter("https://www.whoscored.com", new ObjectMapper(), factory, configuration);
    }

    private String[] emptyResponses(int amount) {
        String empty = "{\"paging\":{\"currentPage\":1,\"totalPages\":1,\"totalResults\":0,"
                + "\"resultsPerPage\":500},\"playerTableStats\":[]}";
        String[] responses = new String[amount];
        java.util.Arrays.fill(responses, empty);
        return responses;
    }
}
