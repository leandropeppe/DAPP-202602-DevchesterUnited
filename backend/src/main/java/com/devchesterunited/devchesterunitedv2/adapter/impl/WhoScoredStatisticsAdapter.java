package com.devchesterunited.devchesterunitedv2.adapter.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.devchesterunited.devchesterunitedv2.adapter.ProveedorEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.adapter.WebDriverFactory;
import com.devchesterunited.devchesterunitedv2.adapter.WebDriverSession;
import com.devchesterunited.devchesterunitedv2.adapter.WhoScoredStatisticsConfiguration;
import com.devchesterunited.devchesterunitedv2.adapter.WhoScoredStatisticsException;
import com.devchesterunited.devchesterunitedv2.adapter.dto.WhoScoredStatisticsFeedResponseDTO;
import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.FamiliaEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.IdentidadEstadisticaJugador;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.RegistroEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.SnapshotEstadisticasJugador;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/** Adapter de WhoScored basado en Selenium y statisticsfeed. */
@Component
public class WhoScoredStatisticsAdapter implements ProveedorEstadisticasJugador {
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final WebDriverFactory webDriverFactory;
    private final WhoScoredStatisticsConfiguration statisticsConfiguration;

    @Autowired
    public WhoScoredStatisticsAdapter(org.springframework.core.env.Environment environment,
            WebDriverFactory webDriverFactory,
            WhoScoredStatisticsConfiguration statisticsConfiguration) {
        this(environment.getProperty("whoscored.base-url", ""), new ObjectMapper(),
                webDriverFactory, statisticsConfiguration);
    }

    WhoScoredStatisticsAdapter(String baseUrl, ObjectMapper objectMapper,
            WebDriverFactory webDriverFactory,
            WhoScoredStatisticsConfiguration statisticsConfiguration) {
        this.baseUrl = quitarBarrasFinales(Objects.requireNonNull(baseUrl));
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.webDriverFactory = webDriverFactory;
        this.statisticsConfiguration = statisticsConfiguration;
    }

    @Override
    public List<SnapshotEstadisticasJugador> obtenerSnapshots(
            Collection<CompeticionEstadistica> competiciones) {
        Objects.requireNonNull(competiciones, "Las competiciones son obligatorias.");
        List<CompeticionEstadistica> solicitadas = List.copyOf(competiciones);
        try (WebDriverSession session = WebDriverSession.open(webDriverFactory)) {
            return solicitadas.stream()
                    .map(competicion -> obtenerSnapshot(competicion, session))
                    .toList();
        } catch (WhoScoredStatisticsException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new WhoScoredStatisticsException("No se pudo adquirir statisticsfeed de WhoScored.", exception);
        }
    }

    @Override
    public SnapshotEstadisticasJugador obtenerSnapshot(CompeticionEstadistica competicion) {
        if (webDriverFactory == null || statisticsConfiguration == null) {
            throw new WhoScoredStatisticsException("WhoScored no esta configurado para statisticsfeed.");
        }
        try (WebDriverSession session = WebDriverSession.open(webDriverFactory)) {
            return obtenerSnapshot(competicion, session);
        } catch (WhoScoredStatisticsException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new WhoScoredStatisticsException("No se pudo adquirir statisticsfeed de WhoScored.", exception);
        }
    }

    /** Adquiere una competicion usando una sesion cuyo ciclo pertenece al llamador. */
    public SnapshotEstadisticasJugador obtenerSnapshot(CompeticionEstadistica competicion,
            WebDriverSession session) {
        Objects.requireNonNull(competicion, "La competicion es obligatoria.");
        Objects.requireNonNull(session, "La sesion WebDriver es obligatoria.");
        Map<FamiliaEstadistica, WhoScoredStatisticsFeedResponseDTO> respuestas =
                new EnumMap<>(FamiliaEstadistica.class);
        for (FamiliaFeed familia : FamiliaFeed.values()) {
            respuestas.put(familia.familiaEstadistica(), adquirirFamilia(competicion, familia, session.driver()));
        }
        return consolidar(competicion, respuestas);
    }

    private WhoScoredStatisticsFeedResponseDTO adquirirFamilia(CompeticionEstadistica competicion,
            FamiliaFeed familia, WebDriver driver) {
        try {
            String url = construirUrl(competicion, familia);
            driver.get(url);
            WebElement bodyElement = new WebDriverWait(driver, Duration.ofSeconds(30))
                    .until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
            String body = bodyElement.getDomProperty("textContent");
            if (body == null || body.isBlank()) {
                throw new WhoScoredStatisticsException("WhoScored devolvio un body vacio.");
            }
            WhoScoredStatisticsFeedResponseDTO response = objectMapper.readValue(
                    body, WhoScoredStatisticsFeedResponseDTO.class);
            validarCompletitud(response, competicion, familia, url);
            return response;
        } catch (WhoScoredStatisticsException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new WhoScoredStatisticsException("No se pudo leer el JSON de statisticsfeed.", exception);
        }
    }

    private void validarCompletitud(WhoScoredStatisticsFeedResponseDTO response,
            CompeticionEstadistica competicion, FamiliaFeed familia, String url) {
        List<String> failures = new java.util.ArrayList<>();
        if (response == null) {
            failures.add("response == null");
            throw errorDeCompletitud(new CompletitudContext(competicion, familia, null, null, null,
                    null, null, url, failures));
        }
        Integer currentPage = response.currentPage();
        Integer totalPages = response.totalPages();
        Integer resultsPerPage = response.resultsPerPage();
        Integer totalResults = response.totalResults();
        Integer playersReceived = response.players() == null ? null : response.players().size();
        agregarFallosMetadata(response, currentPage, totalPages, resultsPerPage, totalResults,
                playersReceived, failures);
        if (!failures.isEmpty()) {
            throw errorDeCompletitud(new CompletitudContext(competicion, familia, currentPage,
                    totalPages, resultsPerPage, totalResults, playersReceived, url, failures));
        }
    }

    private static String quitarBarrasFinales(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return end == value.length() ? value : value.substring(0, end);
    }

    private void agregarFallosMetadata(WhoScoredStatisticsFeedResponseDTO response,
            Integer currentPage, Integer totalPages, Integer resultsPerPage, Integer totalResults,
            Integer playersReceived, List<String> failures) {
        agregarFallosNulos(response, currentPage, totalPages, resultsPerPage, failures);
        agregarFallosRangos(currentPage, totalPages, totalResults, resultsPerPage, failures);
        if (totalResults != null && resultsPerPage != null && totalResults > resultsPerPage) {
            failures.add("totalResults > resultsPerPage");
        }
        if (playersReceived != null && totalResults != null && !playersReceived.equals(totalResults)) {
            failures.add("playersRecibidos != totalResults");
        }
    }

    private void agregarFallosNulos(WhoScoredStatisticsFeedResponseDTO response,
            Integer currentPage, Integer totalPages, Integer resultsPerPage, List<String> failures) {
        if (currentPage == null) failures.add("currentPage == null");
        if (totalPages == null) failures.add("totalPages == null");
        if (response.totalResults() == null) failures.add("totalResults == null");
        if (resultsPerPage == null) failures.add("resultsPerPage == null");
        if (response.players() == null) failures.add("playerTableStats == null");
    }

    private void agregarFallosRangos(Integer currentPage, Integer totalPages, Integer totalResults,
            Integer resultsPerPage, List<String> failures) {
        if (currentPage != null && currentPage < 1) failures.add("currentPage < 1");
        if (totalPages != null && totalPages < 1) failures.add("totalPages < 1");
        if (totalResults != null && totalResults < 0) failures.add("totalResults < 0");
        if (resultsPerPage != null && resultsPerPage < 1) failures.add("resultsPerPage < 1");
        if (currentPage != null && currentPage != 1) failures.add("currentPage != 1");
        if (totalPages != null && totalPages != 1) failures.add("totalPages != 1");
    }

    private WhoScoredStatisticsException errorDeCompletitud(CompletitudContext context) {
        return new WhoScoredStatisticsException("WhoScored devolvio una respuesta paginada o incompleta: "
                + "competicion=" + context.competicion() + ", familia=" + context.familia()
                + ", currentPage=" + context.currentPage() + ", totalPages=" + context.totalPages()
                + ", resultsPerPage=" + context.resultsPerPage() + ", totalResults=" + context.totalResults()
                + ", playersRecibidos=" + context.playersReceived() + ", adquisicion="
                + sanitizarAdquisicion(context.url()) + ", fallos=" + context.failures());
    }

    private record CompletitudContext(CompeticionEstadistica competicion, FamiliaFeed familia,
            Integer currentPage, Integer totalPages, Integer resultsPerPage, Integer totalResults,
            Integer playersReceived, String url, List<String> failures) { }

    private String sanitizarAdquisicion(String url) {
        return url == null ? null : url.replaceAll(
                "(?i)(api[-_]?key|token|authorization|password|secret)=[^&]*", "$1=[REDACTED]");
    }

    private String construirUrl(CompeticionEstadistica competicion, FamiliaFeed familia) {
        var pair = statisticsConfiguration.forCompetition(competicion);
        String endpoint = statisticsConfiguration.getEndpoint();
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            if (baseUrl.isBlank()) throw new WhoScoredStatisticsException("WhoScored no esta configurado.");
            endpoint = baseUrl + (endpoint.startsWith("/") ? endpoint : "/" + endpoint);
        }
        return UriComponentsBuilder.fromUriString(endpoint)
                .queryParam("category", familia.category())
                .queryParam("subcategory", familia.subcategory())
                .queryParam("statsAccumulationType", statisticsConfiguration.getStatsAccumulationType())
                .queryParam("isCurrent", statisticsConfiguration.isCurrent())
                .queryParam("stageId", pair.getStageId())
                .queryParam("tournamentOptions", pair.getTournamentOptions())
                .queryParam("sortBy", statisticsConfiguration.getSortBy())
                .queryParam("field", statisticsConfiguration.getField())
                .queryParam("isMinApp", statisticsConfiguration.isMinApp())
                .queryParam("numberOfPlayersToPick", statisticsConfiguration.getNumberOfPlayersToPick())
                .build().encode().toUriString();
    }

    private SnapshotEstadisticasJugador consolidar(CompeticionEstadistica competicion,
            Map<FamiliaEstadistica, WhoScoredStatisticsFeedResponseDTO> respuestas) {
        Map<Long, JugadorAcumulado> jugadores = new LinkedHashMap<>();
        for (FamiliaFeed familia : FamiliaFeed.values()) {
            var response = respuestas.get(familia.familiaEstadistica());
            for (var player : response.players()) {
                jugadores.computeIfAbsent(validarPlayer(player), JugadorAcumulado::new)
                        .aplicar(player, familia);
            }
        }
        return new SnapshotEstadisticasJugador(competicion,
                jugadores.values().stream().map(JugadorAcumulado::registro).toList());
    }

    private long validarPlayer(WhoScoredStatisticsFeedResponseDTO.PlayerDTO player) {
        if (player == null || player.playerId() == null || player.playerId() <= 0
                || player.name() == null || player.name().isBlank()) {
            throw new WhoScoredStatisticsException("WhoScored no proporciono identidad suficiente del jugador.");
        }
        return player.playerId();
    }

    private static final String SUMMARY_CATEGORY = "summary";

    private enum FamiliaFeed {
        SUMMARY(FamiliaEstadistica.GENERAL, SUMMARY_CATEGORY, "all"),
        OFFENSIVE(FamiliaEstadistica.OFENSIVA, SUMMARY_CATEGORY, "offensive"),
        DEFENSIVE(FamiliaEstadistica.DEFENSIVA, SUMMARY_CATEGORY, "defensive");

        private final FamiliaEstadistica familiaEstadistica;
        private final String category;
        private final String subcategory;

        FamiliaFeed(FamiliaEstadistica familiaEstadistica, String category, String subcategory) {
            this.familiaEstadistica = familiaEstadistica;
            this.category = category;
            this.subcategory = subcategory;
        }

        private FamiliaEstadistica familiaEstadistica() { return familiaEstadistica; }
        private String category() { return category; }
        private String subcategory() { return subcategory; }
    }

    private static final class JugadorAcumulado {
        private final long playerId;
        private String name;
        private Long teamId;
        private String teamName;
        private Integer minsPlayed;
        private Integer goal;
        private Integer assistTotal;
        private BigDecimal shotsPerGame;
        private BigDecimal keyPassPerGame;
        private BigDecimal dribbleWonPerGame;
        private BigDecimal tacklePerGame;
        private BigDecimal rating;
        private Integer yellowCard;
        private Integer redCard;

        private JugadorAcumulado(long playerId) { this.playerId = playerId; }

        private void aplicar(WhoScoredStatisticsFeedResponseDTO.PlayerDTO player, FamiliaFeed familia) {
            name = name == null ? normalizar(player.name()) : name;
            teamId = teamId == null ? player.teamId() : teamId;
            teamName = teamName == null ? normalizarOpcional(player.teamName()) : teamName;
            minsPlayed = preferir(minsPlayed, player.minsPlayed());
            goal = preferir(goal, player.goal());
            assistTotal = preferir(assistTotal, player.assistTotal());
            shotsPerGame = preferir(shotsPerGame, player.shotsPerGame());
            if (familia == FamiliaFeed.OFFENSIVE && player.keyPassPerGame() != null) {
                keyPassPerGame = player.keyPassPerGame();
            } else {
                keyPassPerGame = preferir(keyPassPerGame, player.keyPassPerGame());
            }
            if (familia == FamiliaFeed.OFFENSIVE && player.dribbleWonPerGame() != null) {
                dribbleWonPerGame = player.dribbleWonPerGame();
            } else {
                dribbleWonPerGame = preferir(dribbleWonPerGame, player.dribbleWonPerGame());
            }
            if (familia == FamiliaFeed.DEFENSIVE && player.tacklePerGame() != null) {
                tacklePerGame = player.tacklePerGame();
            } else {
                tacklePerGame = preferir(tacklePerGame, player.tacklePerGame());
            }
            rating = preferir(rating, player.rating());
            yellowCard = preferir(yellowCard, player.yellowCard());
            redCard = preferir(redCard, player.redCard());
        }

        private RegistroEstadisticasJugador registro() {
            return new RegistroEstadisticasJugador(
                    new IdentidadEstadisticaJugador(playerId, name, null, null, teamId, teamName),
                    FamiliaEstadistica.GENERAL,
                    new MetricasEstadisticasJugador(minsPlayed, goal, assistTotal, shotsPerGame,
                            keyPassPerGame, dribbleWonPerGame, tacklePerGame, rating, yellowCard, redCard));
        }

        private static <T> T preferir(T current, T candidate) {
            return current == null ? candidate : current;
        }

        private static String normalizar(String value) { return value.trim().replaceAll("\\s+", " "); }

        private static String normalizarOpcional(String value) {
            return value == null || value.isBlank() ? null : normalizar(value);
        }
    }
}
