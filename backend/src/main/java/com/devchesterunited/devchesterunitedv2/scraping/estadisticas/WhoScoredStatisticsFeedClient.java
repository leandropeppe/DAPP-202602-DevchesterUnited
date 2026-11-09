package com.devchesterunited.devchesterunitedv2.scraping.estadisticas;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.CompeticionEstadistica;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

/** Adquiere y deserializa una familia del feed de estadisticas de WhoScored. */
public final class WhoScoredStatisticsFeedClient {
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final WhoScoredStatisticsConfiguration configuration;

    WhoScoredStatisticsFeedClient(String baseUrl, ObjectMapper objectMapper,
            WhoScoredStatisticsConfiguration configuration) {
        this.baseUrl = quitarBarrasFinales(Objects.requireNonNull(baseUrl));
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.configuration = configuration;
    }

    Resultado adquirir(CompeticionEstadistica competicion, String category,
            String subcategory, WebDriverSession session) {
        try {
            String url = construirUrl(competicion, category, subcategory);
            WebDriver driver = session.driver();
            driver.get(url);
            WebElement bodyElement = new WebDriverWait(driver, Duration.ofSeconds(30))
                    .until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
            String body = bodyElement.getDomProperty("textContent");
            if (body == null || body.isBlank()) {
                throw new WhoScoredStatisticsException("WhoScored devolvio un body vacio.");
            }
            WhoScoredStatisticsFeedResponseDTO response = objectMapper.readValue(
                    body, WhoScoredStatisticsFeedResponseDTO.class);
            return new Resultado(response, url);
        } catch (WhoScoredStatisticsException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new WhoScoredStatisticsException("No se pudo leer el JSON de statisticsfeed.", exception);
        }
    }

    private String construirUrl(CompeticionEstadistica competicion, String category,
            String subcategory) {
        var pair = configuration.forCompetition(competicion);
        String endpoint = configuration.getEndpoint();
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            if (baseUrl.isBlank()) {
                throw new WhoScoredStatisticsException("WhoScored no esta configurado.");
            }
            endpoint = baseUrl + (endpoint.startsWith("/") ? endpoint : "/" + endpoint);
        }
        return UriComponentsBuilder.fromUriString(endpoint)
                .queryParam("category", category)
                .queryParam("subcategory", subcategory)
                .queryParam("statsAccumulationType", configuration.getStatsAccumulationType())
                .queryParam("isCurrent", configuration.isCurrent())
                .queryParam("stageId", pair.getStageId())
                .queryParam("tournamentOptions", pair.getTournamentOptions())
                .queryParam("sortBy", configuration.getSortBy())
                .queryParam("field", configuration.getField())
                .queryParam("isMinApp", configuration.isMinApp())
                .queryParam("numberOfPlayersToPick", configuration.getNumberOfPlayersToPick())
                .build().encode().toUriString();
    }

    private static String quitarBarrasFinales(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return end == value.length() ? value : value.substring(0, end);
    }

    record Resultado(WhoScoredStatisticsFeedResponseDTO response, String url) { }
}
