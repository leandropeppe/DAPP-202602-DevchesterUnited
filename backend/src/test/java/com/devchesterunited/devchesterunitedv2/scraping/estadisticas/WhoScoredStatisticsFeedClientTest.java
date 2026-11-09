package com.devchesterunited.devchesterunitedv2.scraping.estadisticas;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.CompeticionEstadistica;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WhoScoredStatisticsFeedClientTest {
    @Test
    void adquiereUnaFamiliaConLaSesionRecibidaYDevuelveLaUrlFinal() {
        WebDriver driver = mock(WebDriver.class);
        WebElement body = mock(WebElement.class);
        when(driver.findElement(any(By.class))).thenReturn(body);
        when(body.getDomProperty("textContent")).thenReturn(response());
        WhoScoredStatisticsFeedClient client = client("https://example.test", "/statisticsfeed");

        try (WebDriverSession session = session(driver)) {
            WhoScoredStatisticsFeedClient.Resultado resultado = client.adquirir(
                    CompeticionEstadistica.PREMIER_LEAGUE, "summary", "all", session);

            assertThat(resultado.response()).isNotNull();
            assertThat(resultado.url()).startsWith("https://example.test/statisticsfeed?")
                    .contains("category=summary", "subcategory=all", "stageId=25544");
            verify(driver).get(resultado.url());
        }
        verify(driver).quit();
    }

    @Test
    void traduceBodyVacioSinCambiarElMensajeDeAdquisicion() {
        WebDriver driver = mock(WebDriver.class);
        WebElement body = mock(WebElement.class);
        when(driver.findElement(any(By.class))).thenReturn(body);
        when(body.getDomProperty("textContent")).thenReturn("   ");
        WhoScoredStatisticsFeedClient client = client("https://example.test", "/statisticsfeed");

        try (WebDriverSession session = session(driver)) {
            assertThatThrownBy(() -> client.adquirir(
                    CompeticionEstadistica.PREMIER_LEAGUE, "summary", "all", session))
                    .isInstanceOf(WhoScoredStatisticsException.class)
                    .hasMessage("WhoScored devolvio un body vacio.");
        }
    }

    @Test
    void traduceFalloDeNavegacionYConservaLaSesionParaElLlamador() {
        WebDriver driver = mock(WebDriver.class);
        doThrow(new RuntimeException("fallo de navegacion")).when(driver).get(anyString());
        WhoScoredStatisticsFeedClient client = client("https://example.test", "/statisticsfeed");

        try (WebDriverSession session = session(driver)) {
            assertThatThrownBy(() -> client.adquirir(
                    CompeticionEstadistica.PREMIER_LEAGUE, "summary", "all", session))
                    .isInstanceOf(WhoScoredStatisticsException.class)
                    .hasMessage("No se pudo leer el JSON de statisticsfeed.");
            verify(driver).get(anyString());
        }
        verify(driver).quit();
    }

    private WebDriverSession session(WebDriver driver) {
        WebDriverFactory factory = mock(WebDriverFactory.class);
        when(factory.create()).thenReturn(driver);
        return WebDriverSession.open(factory);
    }

    private WhoScoredStatisticsFeedClient client(String baseUrl, String endpoint) {
        WhoScoredStatisticsConfiguration configuration = new WhoScoredStatisticsConfiguration();
        configuration.setEndpoint(endpoint);
        WhoScoredStatisticsConfiguration.CompetitionParameters pair =
                new WhoScoredStatisticsConfiguration.CompetitionParameters();
        pair.setTournamentOptions(2);
        pair.setStageId(25544);
        configuration.setCompetitions(Map.of("premier-league", pair));
        return new WhoScoredStatisticsFeedClient(baseUrl, new ObjectMapper(), configuration);
    }

    private String response() {
        return "{\"paging\":{\"currentPage\":1,\"totalPages\":1,\"totalResults\":0,"
                + "\"resultsPerPage\":500},\"playerTableStats\":[]}";
    }
}
