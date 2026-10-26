package com.devchesterunited.devchesterunitedv2.scraping.estadisticas;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.CompeticionEstadistica;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configuracion interna de infraestructura para el feed de estadisticas. */
@Component
@ConfigurationProperties(prefix = "whoscored.statistics")
public class WhoScoredStatisticsConfiguration {
    private String endpoint = "/statisticsfeed/1/getplayerstatistics";
    private int statsAccumulationType;
    private boolean current;
    private String sortBy;
    private String field;
    private boolean minApp;
    private int numberOfPlayersToPick;
    private boolean passingEnabled;
    private Map<String, CompetitionParameters> competitions = new HashMap<>();

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public int getStatsAccumulationType() { return statsAccumulationType; }
    public void setStatsAccumulationType(int value) { this.statsAccumulationType = value; }
    public boolean isCurrent() { return current; }
    public void setCurrent(boolean value) { this.current = value; }
    public String getSortBy() { return sortBy; }
    public void setSortBy(String value) { this.sortBy = value; }
    public String getField() { return field; }
    public void setField(String value) { this.field = value; }
    public boolean isMinApp() { return minApp; }
    public void setMinApp(boolean value) { this.minApp = value; }
    public int getNumberOfPlayersToPick() { return numberOfPlayersToPick; }
    public void setNumberOfPlayersToPick(int value) { this.numberOfPlayersToPick = value; }
    public boolean isPassingEnabled() { return passingEnabled; }
    public void setPassingEnabled(boolean value) { this.passingEnabled = value; }
    public Map<String, CompetitionParameters> getCompetitions() { return competitions; }
    public void setCompetitions(Map<String, CompetitionParameters> value) {
        this.competitions = value == null ? new HashMap<>() : value;
    }

    /** Devuelve la pareja de una competicion sin exponer IDs al dominio. */
    public CompetitionParameters forCompetition(CompeticionEstadistica competition) {
        String key = competition.name().toLowerCase(Locale.ROOT).replace('_', '-');
        CompetitionParameters parameters = competitions.get(key);
        if (parameters == null) {
            throw new IllegalArgumentException("No hay configuracion de WhoScored para la competicion.");
        }
        return parameters;
    }

    /** Pareja de identificadores propia de la infraestructura WhoScored. */
    public static class CompetitionParameters {
        private int tournamentOptions;
        private int stageId;

        public int getTournamentOptions() { return tournamentOptions; }
        public void setTournamentOptions(int value) { this.tournamentOptions = value; }
        public int getStageId() { return stageId; }
        public void setStageId(int value) { this.stageId = value; }
    }
}
