package com.devchesterunited.devchesterunitedv2.scraping.estadisticas;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.FamiliaEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.SnapshotEstadisticasJugador;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Adapter de WhoScored basado en Selenium y statisticsfeed. */
@Component
public class WhoScoredStatisticsAdapter implements ProveedorEstadisticasJugador {
    private static final Logger LOGGER = LoggerFactory.getLogger(WhoScoredStatisticsAdapter.class);

    private final WebDriverFactory webDriverFactory;
    private final WhoScoredStatisticsConfiguration statisticsConfiguration;
    private final WhoScoredStatisticsFeedClient feedClient;
    private final WhoScoredStatisticsResponseValidator responseValidator;
    private final WhoScoredStatisticsSnapshotAssembler snapshotAssembler;

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
        this.snapshotAssembler = new WhoScoredStatisticsSnapshotAssembler();
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
        return snapshotAssembler.ensamblar(competicion, respuestas,
                statisticsConfiguration.isPassingEnabled());
    }

    private WhoScoredStatisticsFeedResponseDTO adquirirFamilia(CompeticionEstadistica competicion,
            FamiliaFeed familia, WebDriverSession session) {
        try {
            WhoScoredStatisticsFeedClient.Resultado primera = feedClient.adquirir(
                    competicion, familia.category(), familia.subcategory(), 1, session);
            responseValidator.validarPagina(primera.response(), competicion, familia.name(),
                    primera.url(), 1);

            int totalPages = primera.response().totalPages();
            int registrosRecibidos = primera.response().players().size();
            Map<RegistroEquipoKey, WhoScoredStatisticsFeedResponseDTO.PlayerDTO> jugadores =
                    new LinkedHashMap<>();
            incorporar(jugadores, primera.response().players(), competicion, familia, 1);
            for (int page = 2; page <= totalPages; page++) {
                WhoScoredStatisticsFeedClient.Resultado siguiente = feedClient.adquirir(
                        competicion, familia.category(), familia.subcategory(), page, session);
                responseValidator.validarPagina(siguiente.response(), competicion, familia.name(),
                        siguiente.url(), page);
                comprobarMetadata(primera.response(), siguiente.response(), competicion,
                        familia, siguiente.url());
                registrosRecibidos += siguiente.response().players().size();
                incorporar(jugadores, siguiente.response().players(), competicion, familia, page);
            }

            WhoScoredStatisticsFeedResponseDTO consolidada = new WhoScoredStatisticsFeedResponseDTO(
                    new WhoScoredStatisticsFeedResponseDTO.PagingDTO(1, 1,
                            primera.response().resultsPerPage(), primera.response().totalResults()),
                    new java.util.ArrayList<>(jugadores.values()));
            responseValidator.validarConsolidada(consolidada, registrosRecibidos,
                    competicion, familia.name(), primera.url());
            return consolidada;
        } catch (WhoScoredStatisticsException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new WhoScoredStatisticsException("No se pudo leer el JSON de statisticsfeed.", exception);
        }
    }

    private void incorporar(
            Map<RegistroEquipoKey, WhoScoredStatisticsFeedResponseDTO.PlayerDTO> jugadores,
            List<WhoScoredStatisticsFeedResponseDTO.PlayerDTO> pagina,
            CompeticionEstadistica competicion,
            FamiliaFeed familia,
            int page) {

        if (pagina == null) {
            return;
        }

        pagina.forEach(player ->
                incorporarJugador(jugadores, player, competicion, familia, page));
    }

    private void incorporarJugador(
            Map<RegistroEquipoKey, WhoScoredStatisticsFeedResponseDTO.PlayerDTO> jugadores,
            WhoScoredStatisticsFeedResponseDTO.PlayerDTO player,
            CompeticionEstadistica competicion,
            FamiliaFeed familia,
            int page) {

        RegistroEquipoKey key = RegistroEquipoKey.of(player);

        if (jugadores.containsKey(key)
                && !Objects.equals(jugadores.get(key), player)) {
            registrarConflicto(
                    jugadores.get(key),
                    player,
                    key,
                    competicion,
                    familia,
                    page
            );
        }

        if (player == null) {
            throw new WhoScoredStatisticsException(
                    "WhoScored no proporciono identidad suficiente del jugador."
            );
        }

        jugadores.computeIfAbsent(key, k -> player);
    }

    private void registrarConflicto(
            WhoScoredStatisticsFeedResponseDTO.PlayerDTO anterior,
            WhoScoredStatisticsFeedResponseDTO.PlayerDTO player,
            RegistroEquipoKey key,
            CompeticionEstadistica competicion,
            FamiliaFeed familia,
            int page) {

        Long playerId = player == null ? null : player.playerId();
        String diferencias = diferencias(anterior, player);

        LOGGER.warn("Conflicto de registro duplicado WhoScored: competicion={}, familia={}, "
                        + "pagina={}, playerId={}, anteriorTeamId={}, anteriorTeamName={}, "
                        + "nuevoTeamId={}, nuevoTeamName={}, diferencias={}",
                competicion, familia.name(), page, playerId,
                anterior == null ? null : anterior.teamId(),
                anterior == null ? null : anterior.teamName(),
                player == null ? null : player.teamId(),
                player == null ? null : player.teamName(),
                diferencias);

        throw new WhoScoredStatisticsException(
                "WhoScored devolvio registros duplicados conflictivos para un mismo jugador: "
                        + "playerId=" + playerId
                        + ", teamId=" + key.teamId()
                        + ", competicion=" + competicion
                        + ", familia=" + familia.name()
                        + ", pagina=" + page
                        + ", diferencias=" + diferencias);
    }

    private record RegistroEquipoKey(Long playerId, Long teamId, String teamName) {
        private static RegistroEquipoKey of(WhoScoredStatisticsFeedResponseDTO.PlayerDTO player) {
            Long teamId = player == null ? null : player.teamId();
            return new RegistroEquipoKey(player == null ? null : player.playerId(), teamId,
                    teamId == null && player != null ? player.teamName() : null);
        }
    }

    private String diferencias(WhoScoredStatisticsFeedResponseDTO.PlayerDTO anterior,
            WhoScoredStatisticsFeedResponseDTO.PlayerDTO nuevo) {
        if (anterior == null || nuevo == null) {
            return "registroAnterior=" + anterior + ", registroNuevo=" + nuevo;
        }
        StringBuilder diferencias = new StringBuilder();
        agregarDiferencia(diferencias, "name", anterior.name(), nuevo.name());
        agregarDiferencia(diferencias, "teamId", anterior.teamId(), nuevo.teamId());
        agregarDiferencia(diferencias, "teamName", anterior.teamName(), nuevo.teamName());
        agregarDiferencia(diferencias, "minsPlayed", anterior.minsPlayed(), nuevo.minsPlayed());
        agregarDiferencia(diferencias, "goal", anterior.goal(), nuevo.goal());
        agregarDiferencia(diferencias, "assistTotal", anterior.assistTotal(), nuevo.assistTotal());
        agregarDiferencia(diferencias, "shotsPerGame", anterior.shotsPerGame(), nuevo.shotsPerGame());
        agregarDiferencia(diferencias, "keyPassPerGame", anterior.keyPassPerGame(), nuevo.keyPassPerGame());
        agregarDiferencia(diferencias, "dribbleWonPerGame", anterior.dribbleWonPerGame(), nuevo.dribbleWonPerGame());
        agregarDiferencia(diferencias, "tacklePerGame", anterior.tacklePerGame(), nuevo.tacklePerGame());
        agregarDiferencia(diferencias, "rating", anterior.rating(), nuevo.rating());
        agregarDiferencia(diferencias, "yellowCard", anterior.yellowCard(), nuevo.yellowCard());
        agregarDiferencia(diferencias, "redCard", anterior.redCard(), nuevo.redCard());
        agregarDiferencia(diferencias, "apps", anterior.apps(), nuevo.apps());
        agregarDiferencia(diferencias, "subOn", anterior.subOn(), nuevo.subOn());
        agregarDiferencia(diferencias, "manOfTheMatch", anterior.manOfTheMatch(), nuevo.manOfTheMatch());
        agregarDiferencia(diferencias, "aerialWonPerGame", anterior.aerialWonPerGame(), nuevo.aerialWonPerGame());
        agregarDiferencia(diferencias, "foulGivenPerGame", anterior.foulGivenPerGame(), nuevo.foulGivenPerGame());
        agregarDiferencia(diferencias, "offsideGivenPerGame", anterior.offsideGivenPerGame(), nuevo.offsideGivenPerGame());
        agregarDiferencia(diferencias, "dispossessedPerGame", anterior.dispossessedPerGame(), nuevo.dispossessedPerGame());
        agregarDiferencia(diferencias, "turnoverPerGame", anterior.turnoverPerGame(), nuevo.turnoverPerGame());
        agregarDiferencia(diferencias, "interceptionPerGame", anterior.interceptionPerGame(), nuevo.interceptionPerGame());
        agregarDiferencia(diferencias, "foulsPerGame", anterior.foulsPerGame(), nuevo.foulsPerGame());
        agregarDiferencia(diferencias, "offsideWonPerGame", anterior.offsideWonPerGame(), nuevo.offsideWonPerGame());
        agregarDiferencia(diferencias, "clearancePerGame", anterior.clearancePerGame(), nuevo.clearancePerGame());
        agregarDiferencia(diferencias, "wasDribbledPerGame", anterior.wasDribbledPerGame(), nuevo.wasDribbledPerGame());
        agregarDiferencia(diferencias, "outfielderBlockPerGame", anterior.outfielderBlockPerGame(), nuevo.outfielderBlockPerGame());
        agregarDiferencia(diferencias, "goalOwn", anterior.goalOwn(), nuevo.goalOwn());
        agregarDiferencia(diferencias, "totalPassesPerGame", anterior.totalPassesPerGame(), nuevo.totalPassesPerGame());
        agregarDiferencia(diferencias, "passSuccess", anterior.passSuccess(), nuevo.passSuccess());
        agregarDiferencia(diferencias, "accurateCrossesPerGame", anterior.accurateCrossesPerGame(), nuevo.accurateCrossesPerGame());
        agregarDiferencia(diferencias, "accurateLongPassPerGame", anterior.accurateLongPassPerGame(), nuevo.accurateLongPassPerGame());
        agregarDiferencia(diferencias, "accurateThroughBallPerGame", anterior.accurateThroughBallPerGame(), nuevo.accurateThroughBallPerGame());
        return diferencias.isEmpty() ? "ninguna" : diferencias.toString();
    }

    private void agregarDiferencia(StringBuilder diferencias, String campo, Object anterior, Object nuevo) {
        if (!Objects.equals(anterior, nuevo)) {
            if (!diferencias.isEmpty()) diferencias.append("; ");
            diferencias.append(campo).append("[").append(anterior).append(" -> ").append(nuevo).append("]");
        }
    }

    private void comprobarMetadata(WhoScoredStatisticsFeedResponseDTO primera,
            WhoScoredStatisticsFeedResponseDTO siguiente, CompeticionEstadistica competicion,
            FamiliaFeed familia, String url) {
        if (!java.util.Objects.equals(primera.totalPages(), siguiente.totalPages())
                || !java.util.Objects.equals(primera.totalResults(), siguiente.totalResults())
                || !java.util.Objects.equals(primera.resultsPerPage(), siguiente.resultsPerPage())) {
            throw new WhoScoredStatisticsException("WhoScored devolvio metadata de paginacion inconsistente: "
                    + "competicion=" + competicion + ", familia=" + familia.name()
                    + ", adquisicion=" + url);
        }
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

}
