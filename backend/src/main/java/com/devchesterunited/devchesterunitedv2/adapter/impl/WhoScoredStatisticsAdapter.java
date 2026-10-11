package com.devchesterunited.devchesterunitedv2.adapter.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.devchesterunited.devchesterunitedv2.adapter.ProveedorEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.adapter.WebDriverFactory;
import com.devchesterunited.devchesterunitedv2.adapter.WebDriverSession;
import com.devchesterunited.devchesterunitedv2.adapter.WhoScoredStatisticsConfiguration;
import com.devchesterunited.devchesterunitedv2.adapter.WhoScoredStatisticsException;
import com.devchesterunited.devchesterunitedv2.adapter.dto.WhoScoredStatisticsFeedResponseDTO;
import com.devchesterunited.devchesterunitedv2.adapter.dto.WhoScoredStatisticsResponseDTO;
import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.FamiliaEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.IdentidadEstadisticaJugador;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.PaginaEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.RegistroEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.SnapshotEstadisticasJugador;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/** Adapter de WhoScored. El formato HTTP/JSON queda completamente confinado aquí. */
@Component
public class WhoScoredStatisticsAdapter implements ProveedorEstadisticasJugador {
    private final RestClient client;
    private final String baseUrl;
    private final String apiKey;
    private final int maxResponseBytes;
    private final ObjectMapper objectMapper;
    private final WebDriverFactory webDriverFactory;
    private final WhoScoredStatisticsConfiguration statisticsConfiguration;

    @Autowired
    public WhoScoredStatisticsAdapter(RestClient.Builder builder,
            org.springframework.core.env.Environment environment,
            WebDriverFactory webDriverFactory,
            WhoScoredStatisticsConfiguration statisticsConfiguration) {
        this(builder, environment.getProperty("whoscored.base-url", ""),
                environment.getProperty("whoscored.api-key", ""),
                environment.getProperty("whoscored.max-response-bytes", Integer.class, 10_485_760),
                new ObjectMapper(), webDriverFactory, statisticsConfiguration);
    }

    public WhoScoredStatisticsAdapter(RestClient.Builder builder, String baseUrl,
            String apiKey, int maxResponseBytes) {
        this(builder, baseUrl, apiKey, maxResponseBytes, new ObjectMapper(), null, null);
    }

    WhoScoredStatisticsAdapter(RestClient.Builder builder, String baseUrl,
            String apiKey, int maxResponseBytes, ObjectMapper objectMapper) {
        this(builder, baseUrl, apiKey, maxResponseBytes, objectMapper, null, null);
    }

    WhoScoredStatisticsAdapter(RestClient.Builder builder, String baseUrl,
            String apiKey, int maxResponseBytes, ObjectMapper objectMapper,
            WebDriverFactory webDriverFactory,
            WhoScoredStatisticsConfiguration statisticsConfiguration) {
        this.baseUrl = Objects.requireNonNull(baseUrl).replaceAll("/+$", "");
        this.client = this.baseUrl.isBlank() ? builder.build() : builder.baseUrl(this.baseUrl).build();
        this.apiKey = apiKey == null ? "" : apiKey;
        if (maxResponseBytes < 1) {
            throw new IllegalArgumentException("El límite de respuesta debe ser positivo.");
        }
        this.maxResponseBytes = maxResponseBytes;
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
            List<SnapshotEstadisticasJugador> snapshots = new java.util.ArrayList<>(solicitadas.size());
            for (CompeticionEstadistica competicion : solicitadas) {
                snapshots.add(obtenerSnapshot(competicion, session));
            }
            return List.copyOf(snapshots);
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

    /** Adquiere una competicion usando una sesion cuyo ciclo de vida pertenece al llamador del Adapter. */
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
            driver.get(construirUrl(competicion, familia));
            WebElement bodyElement = new WebDriverWait(driver, Duration.ofSeconds(30))
                    .until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
            String body = bodyElement.getDomProperty("textContent");
            if (body == null || body.isBlank()) {
                throw new WhoScoredStatisticsException("WhoScored devolvio un body vacio.");
            }
            WhoScoredStatisticsFeedResponseDTO response = objectMapper.readValue(
                    body, WhoScoredStatisticsFeedResponseDTO.class);
            validarCompletitud(response);
            return response;
        } catch (WhoScoredStatisticsException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new WhoScoredStatisticsException("No se pudo leer el JSON de statisticsfeed.", exception);
        }
    }

    private void validarCompletitud(WhoScoredStatisticsFeedResponseDTO response) {
        if (response == null || response.currentPage() == null || response.totalPages() == null
                || response.totalResults() == null || response.resultsPerPage() == null
                || response.players() == null) {
            throw new WhoScoredStatisticsException("WhoScored devolvio metadata incompleta.");
        }
        if (response.currentPage() < 1 || response.totalPages() < 1
                || response.totalResults() < 0 || response.resultsPerPage() < 1) {
            throw new WhoScoredStatisticsException("WhoScored devolvio metadata numerica invalida.");
        }
        if (response.currentPage() != 1 || response.totalPages() != 1) {
            throw new WhoScoredStatisticsException("WhoScored devolvio una respuesta paginada o incompleta.");
        }
        if (response.totalResults() > response.resultsPerPage()
                || response.players().size() != response.totalResults()) {
            throw new WhoScoredStatisticsException("WhoScored devolvio una cantidad de jugadores inconsistente.");
        }
    }

    private String construirUrl(CompeticionEstadistica competicion, FamiliaFeed familia) {
        var pair = statisticsConfiguration.forCompetition(competicion);
        String endpoint = statisticsConfiguration.getEndpoint();
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            if (baseUrl.isBlank()) {
                throw new WhoScoredStatisticsException("WhoScored no esta configurado.");
            }
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
                .build()
                .encode()
                .toUriString();
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

    private enum FamiliaFeed {
        SUMMARY(FamiliaEstadistica.GENERAL, "summary", "all"),
        OFFENSIVE(FamiliaEstadistica.OFENSIVA, "summary", "offensive"),
        DEFENSIVE(FamiliaEstadistica.DEFENSIVA, "summary", "defensive");

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
    }

    @Override
    public PaginaEstadisticasJugador obtenerPagina(CompeticionEstadistica competicion,
            FamiliaEstadistica familia, int pagina, int tamano) {
        if (pagina < 1 || tamano < 1) {
            throw new IllegalArgumentException("La página y el tamaño deben ser positivos.");
        }
        if (baseUrl.isBlank()) {
            throw new WhoScoredStatisticsException("WhoScored no está configurado.");
        }
        try {
            WhoScoredStatisticsResponseDTO response = client.get()
                    .uri(uriBuilder -> uriBuilder.path("/statistics")
                            .queryParam("competition", competicion.name())
                            .queryParam("family", familia.name())
                            .queryParam("page", pagina)
                            .queryParam("pageSize", tamano).build())
                    .headers(headers -> {
                        if (!apiKey.isBlank()) {
                            headers.setBearerAuth(apiKey);
                        }
                    })
                    .exchange((request, responseResult) -> {
                        if (responseResult.getStatusCode().value() == 429) {
                            throw new WhoScoredStatisticsException("El proveedor estadístico alcanzó su cuota.");
                        }
                        if (responseResult.getStatusCode().isError()) {
                            throw new WhoScoredStatisticsException("WhoScored devolvió una respuesta no exitosa.");
                        }
                        try {
                            return objectMapper.readValue(leerLimitado(responseResult.getBody()),
                                    WhoScoredStatisticsResponseDTO.class);
                        } catch (IOException exception) {
                            throw new WhoScoredStatisticsException("La respuesta de WhoScored no es un JSON válido.", exception);
                        }
                    });
            return traducir(response, competicion, familia, pagina, tamano);
        } catch (WhoScoredStatisticsException exception) {
            throw exception;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new WhoScoredStatisticsException("No se pudo obtener una página válida de WhoScored.", exception);
        }
    }

    private byte[] leerLimitado(InputStream body) throws IOException {
        byte[] buffer = new byte[8192];
        int total = 0;
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        int leidos;
        while ((leidos = body.read(buffer)) != -1) {
            total += leidos;
            if (total > maxResponseBytes) {
                throw new WhoScoredStatisticsException("La respuesta de WhoScored supera el límite configurado.");
            }
            result.write(buffer, 0, leidos);
        }
        return result.toByteArray();
    }

    private PaginaEstadisticasJugador traducir(WhoScoredStatisticsResponseDTO response,
            CompeticionEstadistica competicion, FamiliaEstadistica familia,
            int requestedPage, int requestedSize) {
        if (response == null || response.page() == null || response.pageSize() == null
                || response.totalPages() == null || response.totalResults() == null
                || response.players() == null || response.page() != requestedPage
                || response.pageSize() != requestedSize || response.totalPages() < requestedPage
                || response.totalPages() < 1 || response.totalResults() < 0
                || response.players().size() > requestedSize
                || response.totalResults() < response.players().size()) {
            throw new WhoScoredStatisticsException("La metadata de paginación de WhoScored es inválida.");
        }
        List<RegistroEstadisticasJugador> registros = response.players().stream()
                .map(player -> traducirJugador(player, familia)).toList();
        return new PaginaEstadisticasJugador(competicion, familia, response.page(),
                response.pageSize(), response.totalPages(), response.totalResults(), registros);
    }

    private RegistroEstadisticasJugador traducirJugador(WhoScoredStatisticsResponseDTO.PlayerDTO player,
            FamiliaEstadistica familia) {
        if (player == null || player.playerId() == null || player.playerId() <= 0
                || player.name() == null || player.name().isBlank()) {
            throw new WhoScoredStatisticsException("WhoScored no proporcionó identidad suficiente del jugador.");
        }
        boolean teamIdAbsent = player.teamId() == null;
        boolean teamNameAbsent = player.teamName() == null || player.teamName().isBlank();
        if (teamIdAbsent != teamNameAbsent) {
            throw new WhoScoredStatisticsException("WhoScored proporcionó hints de equipo inconsistentes.");
        }
        var identidad = new IdentidadEstadisticaJugador(player.playerId(), normalizar(player.name()),
                normalizarOpcional(player.position()), normalizarOpcional(player.nationality()),
                player.teamId(), normalizarOpcional(player.teamName()));
        var metricas = new MetricasEstadisticasJugador(player.minutesPlayed(), player.goals(), player.assists(),
                player.shots(), player.keyPasses(), player.dribbles(), player.tackles(), player.rating(),
                player.yellowCards(), player.redCards());
        return new RegistroEstadisticasJugador(identidad, familia, metricas);
    }

    private static String normalizar(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private static String normalizarOpcional(String value) {
        return value == null || value.isBlank() ? null : normalizar(value);
    }
}
