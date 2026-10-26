package com.devchesterunited.devchesterunitedv2.scraping.estadisticas;

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

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

class WhoScoredStatisticsAdapterTest {
    @Test
    void traduceLasTresFamiliasRealesYConsolidaElSnapshot() throws IOException {
        WebDriver driver = mock(WebDriver.class);
        WebElement body = mock(WebElement.class);
        WebDriverFactory factory = mock(WebDriverFactory.class);
        when(factory.create()).thenReturn(driver);
        when(driver.findElement(any(By.class))).thenReturn(body);
        when(body.getDomProperty("textContent")).thenReturn(
                fixture("summary-all.json"),
                fixture("summary-offensive.json"),
                fixture("summary-defensive.json"));

        var snapshot = adapter(factory).obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE);

        assertThat(snapshot.registros()).singleElement().satisfies(registro -> {
            assertThat(registro.identidad().playerId()).isEqualTo(101);
            assertThat(registro.identidad().teamId()).isEqualTo(201);
            assertThat(registro.identidad().posicion()).isNull();
            assertThat(registro.metricas().minutosJugados()).isEqualTo(900);
            assertThat(registro.metricas().goles()).isEqualTo(8);
            assertThat(registro.metricas().asistencias()).isEqualTo(4);
            assertThat(registro.metricas().tiros()).hasToString("2.40");
            assertThat(registro.metricas().valoracion()).hasToString("7.20");
            assertThat(registro.metricas().pasesClave()).hasToString("1.70");
            assertThat(registro.metricas().regates()).hasToString("2.10");
            assertThat(registro.metricas().tackles()).hasToString("1.90");
            assertThat(registro.metricas().tarjetasAmarillas()).isEqualTo(1);
             assertThat(registro.metricas().tarjetasRojas()).isZero();
        });
        verify(factory).create();
        verify(driver, times(3)).get(any(String.class));
        verify(driver).quit();
    }

    @Test
    void ignoraCamposAdicionalesSinAmpliarLasMetricasDelSnapshot() throws IOException {
        WebDriver driver = mock(WebDriver.class);
        WebElement body = mock(WebElement.class);
        WebDriverFactory factory = mock(WebDriverFactory.class);
        when(factory.create()).thenReturn(driver);
        when(driver.findElement(any(By.class))).thenReturn(body);
        when(body.getDomProperty("textContent")).thenReturn(
                fixture("summary-all-with-extra-fields.json"),
                fixture("summary-offensive.json"),
                fixture("summary-defensive.json"));

        var snapshot = adapter(factory).obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE);

        assertThat(snapshot.registros()).singleElement().satisfies(registro -> {
            assertThat(registro.identidad().posicion()).isNull();
            assertThat(registro.metricas().minutosJugados()).isEqualTo(900);
            assertThat(registro.metricas().goles()).isEqualTo(8);
            assertThat(registro.metricas().asistencias()).isEqualTo(4);
            assertThat(registro.metricas().tiros()).hasToString("2.40");
            assertThat(registro.metricas().pasesClave()).hasToString("1.70");
            assertThat(registro.metricas().regates()).hasToString("2.10");
            assertThat(registro.metricas().tackles()).hasToString("1.90");
            assertThat(registro.metricas().valoracion()).hasToString("7.20");
            assertThat(registro.metricas().tarjetasAmarillas()).isEqualTo(1);
            assertThat(registro.metricas().tarjetasRojas()).isZero();
        });
        verify(driver, times(3)).get(any(String.class));
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
        var pair = new CompetitionParameters();
        pair.setTournamentOptions(2);
        pair.setStageId(25544);
        configuration.setCompetitions(Map.of("premier-league", pair));
        return new WhoScoredStatisticsAdapter("https://www.whoscored.com", new ObjectMapper(), factory, configuration);
    }

    private String fixture(String name) throws IOException {
        String path = "/fixtures/whoscored/" + name;
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("fixture %s", name).isNotNull();
            return new String(input.readAllBytes());
        }
    }
}
