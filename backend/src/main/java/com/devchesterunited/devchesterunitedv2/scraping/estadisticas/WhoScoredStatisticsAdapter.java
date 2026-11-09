package com.devchesterunited.devchesterunitedv2.scraping.estadisticas;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/** Adapter de WhoScored basado en Selenium y statisticsfeed. */
@Component
public class WhoScoredStatisticsAdapter implements ProveedorEstadisticasJugador {
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
