package com.devchesterunited.devchesterunitedv2.scraping.estadisticas;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/** Adapter de WhoScored basado en Selenium y statisticsfeed. */
@Component
public class WhoScoredStatisticsAdapter implements ProveedorEstadisticasJugador {
    private final WebDriverFactory webDriverFactory;
    private final WhoScoredStatisticsConfiguration statisticsConfiguration;
    private final WhoScoredStatisticsFeedClient feedClient;
    private final WhoScoredStatisticsResponseValidator responseValidator;

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
        this.webDriverFactory = webDriverFactory;
        this.statisticsConfiguration = statisticsConfiguration;
        this.feedClient = new WhoScoredStatisticsFeedClient(baseUrl, objectMapper, statisticsConfiguration);
        this.responseValidator = new WhoScoredStatisticsResponseValidator();
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
            if (familia == FamiliaFeed.PASSING && !statisticsConfiguration.isPassingEnabled()) continue;
            respuestas.put(familia.familiaEstadistica(), adquirirFamilia(competicion, familia, session));
        }
        return consolidar(competicion, respuestas);
    }

    private WhoScoredStatisticsFeedResponseDTO adquirirFamilia(CompeticionEstadistica competicion,
            FamiliaFeed familia, WebDriverSession session) {
        try {
            WhoScoredStatisticsFeedClient.Resultado resultado = feedClient.adquirir(
                    competicion, familia.category(), familia.subcategory(), session);
            responseValidator.validar(resultado.response(), competicion, familia.name(), resultado.url());
            return resultado.response();
        } catch (WhoScoredStatisticsException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new WhoScoredStatisticsException("No se pudo leer el JSON de statisticsfeed.", exception);
        }
    }

    private static String quitarBarrasFinales(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return end == value.length() ? value : value.substring(0, end);
    }

    private SnapshotEstadisticasJugador consolidar(CompeticionEstadistica competicion,
            Map<FamiliaEstadistica, WhoScoredStatisticsFeedResponseDTO> respuestas) {
        Map<Long, JugadorAcumulado> jugadores = new LinkedHashMap<>();
        for (FamiliaFeed familia : FamiliaFeed.values()) {
            if (familia == FamiliaFeed.PASSING && !statisticsConfiguration.isPassingEnabled()) continue;
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
        DEFENSIVE(FamiliaEstadistica.DEFENSIVA, SUMMARY_CATEGORY, "defensive"),
        PASSING(FamiliaEstadistica.PASES, SUMMARY_CATEGORY, "passing");

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
        private Integer apps;
        private Integer subOn;
        private Integer manOfTheMatch;
        private Integer goalOwn;
        private BigDecimal aerialWonPerGame;
        private BigDecimal foulGivenPerGame;
        private BigDecimal offsideGivenPerGame;
        private BigDecimal dispossessedPerGame;
        private BigDecimal turnoverPerGame;
        private BigDecimal interceptionPerGame;
        private BigDecimal foulsPerGame;
        private BigDecimal offsideWonPerGame;
        private BigDecimal clearancePerGame;
        private BigDecimal wasDribbledPerGame;
        private BigDecimal outfielderBlockPerGame;
        private BigDecimal totalPassesPerGame;
        private BigDecimal passSuccess;
        private BigDecimal accurateCrossesPerGame;
        private BigDecimal accurateLongPassPerGame;
        private BigDecimal accurateThroughBallPerGame;

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
            apps = preferir(apps, player.apps()); subOn = preferir(subOn, player.subOn()); manOfTheMatch = preferir(manOfTheMatch, player.manOfTheMatch());
            aerialWonPerGame = preferir(aerialWonPerGame, player.aerialWonPerGame());
            foulGivenPerGame = preferir(foulGivenPerGame, player.foulGivenPerGame()); offsideGivenPerGame = preferir(offsideGivenPerGame, player.offsideGivenPerGame());
            dispossessedPerGame = preferir(dispossessedPerGame, player.dispossessedPerGame()); turnoverPerGame = preferir(turnoverPerGame, player.turnoverPerGame());
            interceptionPerGame = preferir(interceptionPerGame, player.interceptionPerGame()); foulsPerGame = preferir(foulsPerGame, player.foulsPerGame());
            offsideWonPerGame = preferir(offsideWonPerGame, player.offsideWonPerGame()); clearancePerGame = preferir(clearancePerGame, player.clearancePerGame());
            wasDribbledPerGame = preferir(wasDribbledPerGame, player.wasDribbledPerGame()); outfielderBlockPerGame = preferir(outfielderBlockPerGame, player.outfielderBlockPerGame()); goalOwn = preferir(goalOwn, player.goalOwn());
            totalPassesPerGame = preferir(totalPassesPerGame, player.totalPassesPerGame()); passSuccess = preferir(passSuccess, player.passSuccess());
            accurateCrossesPerGame = preferir(accurateCrossesPerGame, player.accurateCrossesPerGame()); accurateLongPassPerGame = preferir(accurateLongPassPerGame, player.accurateLongPassPerGame()); accurateThroughBallPerGame = preferir(accurateThroughBallPerGame, player.accurateThroughBallPerGame());
        }

        private RegistroEstadisticasJugador registro() {
            return new RegistroEstadisticasJugador(
                    new IdentidadEstadisticaJugador(playerId, name, null, null, teamId, teamName),
                    FamiliaEstadistica.GENERAL,
                    new MetricasEstadisticasJugador(
                            new com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasGenerales(apps, subOn, minsPlayed, manOfTheMatch, aerialWonPerGame, rating, yellowCard, redCard),
                            new com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasOfensivas(goal, assistTotal, shotsPerGame, keyPassPerGame, dribbleWonPerGame, foulGivenPerGame, offsideGivenPerGame, dispossessedPerGame, turnoverPerGame),
                            new com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasDefensivas(tacklePerGame, interceptionPerGame, foulsPerGame, offsideWonPerGame, clearancePerGame, wasDribbledPerGame, outfielderBlockPerGame, goalOwn),
                            new com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasPases(totalPassesPerGame, passSuccess, accurateCrossesPerGame, accurateLongPassPerGame, accurateThroughBallPerGame)));
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
