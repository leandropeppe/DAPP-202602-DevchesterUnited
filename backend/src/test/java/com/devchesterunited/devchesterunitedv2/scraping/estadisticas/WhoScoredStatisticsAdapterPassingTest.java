package com.devchesterunited.devchesterunitedv2.scraping.estadisticas;
import com.devchesterunited.devchesterunitedv2.scraping.estadisticas.WhoScoredStatisticsConfiguration.CompetitionParameters;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.CompeticionEstadistica;
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

class WhoScoredStatisticsAdapterPassingTest {
    @Test
    void adquiereLasCuatroCategoriasYConservaLasMetricasDePases() throws IOException {
        WebDriver driver = mock(WebDriver.class);
        WebElement body = mock(WebElement.class);
        WebDriverFactory factory = mock(WebDriverFactory.class);
        when(factory.create()).thenReturn(driver);
        when(driver.findElement(any(By.class))).thenReturn(body);
        when(body.getDomProperty("textContent")).thenReturn(
                fixture("summary-complete.json"), fixture("offensive-complete.json"),
                fixture("defensive-complete.json"), fixture("summary-passing.json"));

        var configuration = new WhoScoredStatisticsConfiguration();
        configuration.setPassingEnabled(true);
        var pair = new CompetitionParameters();
        pair.setTournamentOptions(2);
        pair.setStageId(25544);
        configuration.setCompetitions(Map.of("premier-league", pair));
        var adapter = new WhoScoredStatisticsAdapter("https://example.test", new ObjectMapper(), factory, configuration);

        var snapshot = adapter.obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE);

        assertThat(snapshot.registros()).singleElement().satisfies(registro -> {
            assertThat(registro.metricas().generales().apps()).isEqualTo(12);
            assertThat(registro.metricas().ofensivas().goal()).isEqualTo(8);
            assertThat(registro.metricas().pases().passSuccess()).hasToString("86.44");
        });
        verify(driver, times(4)).get(any(String.class));
        verify(driver).quit();
    }

    private String fixture(String name) throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/fixtures/whoscored/" + name)) {
            return new String(input.readAllBytes());
        }
    }
}
