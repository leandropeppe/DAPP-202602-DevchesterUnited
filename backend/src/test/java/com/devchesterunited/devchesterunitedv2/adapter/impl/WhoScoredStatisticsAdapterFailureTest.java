package com.devchesterunited.devchesterunitedv2.scraping.estadisticas;
import com.devchesterunited.devchesterunitedv2.scraping.estadisticas.WhoScoredStatisticsAdapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.devchesterunited.devchesterunitedv2.scraping.estadisticas.WebDriverFactory;
import com.devchesterunited.devchesterunitedv2.scraping.estadisticas.WhoScoredStatisticsConfiguration;
import com.devchesterunited.devchesterunitedv2.scraping.estadisticas.WhoScoredStatisticsConfiguration.CompetitionParameters;
import com.devchesterunited.devchesterunitedv2.scraping.estadisticas.WhoScoredStatisticsException;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.CompeticionEstadistica;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WebDriverException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class WhoScoredStatisticsAdapterFailureTest {
    @Test
    void rechazaMetadataContractualIncompletaSinSnapshotParcial() throws IOException {
        WebDriver driver = mock(WebDriver.class);
        WebElement body = mock(WebElement.class);
        WebDriverFactory factory = mock(WebDriverFactory.class);
        when(factory.create()).thenReturn(driver);
        when(driver.findElement(any(By.class))).thenReturn(body);
        when(body.getDomProperty("textContent")).thenReturn(fixture("summary-invalid-total-pages.json"));

        var adapter = adapter(factory);
        assertThatThrownBy(() -> adapter.obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(WhoScoredStatisticsException.class);

        verify(driver).get(any(String.class));
        verify(driver).quit();
    }

    @Test
    void traduceFalloDeNavegacionSinExponerDetallesYSesierraElDriver() {
        WebDriver driver = mock(WebDriver.class);
        WebDriverFactory factory = mock(WebDriverFactory.class);
        when(factory.create()).thenReturn(driver);
        doThrow(new WebDriverException(
                "https://whoscored.test/private?stageId=25544&tournamentOptions=2 token=secret"))
                .when(driver).get(any(String.class));


        var error = org.assertj.core.api.Assertions.catchThrowable(
                () -> adapter(factory).obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE));

        assertThat(error).isInstanceOfSatisfying(WhoScoredStatisticsException.class,
                exception -> assertThat(exception).hasMessageContaining("No se pudo leer el JSON de statisticsfeed.")
                        .hasMessageNotContaining("whoscored.test")
                        .hasMessageNotContaining("stageId")
                        .hasMessageNotContaining("tournamentOptions")
                        .hasMessageNotContaining("secret"));
        verify(driver).quit();
    }

    @Test
    void traduceFalloControladoAlEsperarElBodyYSesierraElDriver() {
        WebDriver driver = mock(WebDriver.class);
        WebDriverFactory factory = mock(WebDriverFactory.class);
        when(factory.create()).thenReturn(driver);
        doThrow(new WebDriverException("internal wait detail"))
                .when(driver).findElement(any(By.class));

        var error = org.assertj.core.api.Assertions.catchThrowable(
                () -> adapter(factory).obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE));

        assertThat(error).isInstanceOfSatisfying(WhoScoredStatisticsException.class,
                exception -> assertThat(exception).hasMessage("No se pudo leer el JSON de statisticsfeed.")
                        .hasMessageNotContaining("internal wait detail"));
        verify(driver).quit();
    }

    @Test
    void rechazaBodyVacioSinCrearSnapshotParcial() {
        WebDriver driver = mock(WebDriver.class);
        WebElement body = mock(WebElement.class);
        WebDriverFactory factory = mock(WebDriverFactory.class);
        when(factory.create()).thenReturn(driver);
        when(driver.findElement(any(By.class))).thenReturn(body);
        when(body.getDomProperty("textContent")).thenReturn("   ");

        var adapter = adapter(factory);
        assertThatThrownBy(() -> adapter.obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(WhoScoredStatisticsException.class)
                .hasMessage("WhoScored devolvio un body vacio.");
        verify(driver).quit();
    }

    @Test
    void rechazaJsonMalformadoSinExponerElCuerpoDelProveedor() {
        WebDriver driver = mock(WebDriver.class);
        WebElement body = mock(WebElement.class);
        WebDriverFactory factory = mock(WebDriverFactory.class);
        when(factory.create()).thenReturn(driver);
        when(driver.findElement(any(By.class))).thenReturn(body);
        when(body.getDomProperty("textContent")).thenReturn("{\"privateToken\":\"secret\", malformed");

        var error = org.assertj.core.api.Assertions.catchThrowable(
                () -> adapter(factory).obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE));

        assertThat(error).isInstanceOfSatisfying(WhoScoredStatisticsException.class,
                exception -> assertThat(exception).hasMessage("No se pudo leer el JSON de statisticsfeed.")
                        .hasMessageNotContaining("privateToken")
                        .hasMessageNotContaining("secret")
                        .hasMessageNotContaining("malformed"));
        verify(driver).quit();
    }

    @Test
    void falloDeUnaFamiliaAbortaLaAdquisicionSinSnapshotParcial() throws IOException {
        WebDriver driver = mock(WebDriver.class);
        WebElement body = mock(WebElement.class);
        WebDriverFactory factory = mock(WebDriverFactory.class);
        when(factory.create()).thenReturn(driver);
        when(driver.findElement(any(By.class))).thenReturn(body);
        when(body.getDomProperty("textContent")).thenReturn(fixture("summary-all.json"), "not-json");

        var adapter = adapter(factory);
        assertThatThrownBy(() -> adapter.obtenerSnapshot(CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(WhoScoredStatisticsException.class);
        verify(driver, times(2)).get(any(String.class));
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
            if (input == null) {
                throw new AssertionError("Missing fixture " + name);
            }
            return new String(input.readAllBytes());
        }
    }
}
