package com.devchesterunited.devchesterunitedv2.adapter;

import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhoScoredStatisticsConfigurationTest {
    @Test
    void exponeParametrosComunesYLasCincoParejasConfiguradas() {
        var configuration = new WhoScoredStatisticsConfiguration();
        configuration.setEndpoint("/statisticsfeed/1/getplayerstatistics");
        configuration.setStatsAccumulationType(0);
        configuration.setCurrent(true);
        configuration.setSortBy("Rating");
        configuration.setField("Overall");
        configuration.setMinApp(false);
        configuration.setNumberOfPlayersToPick(500);
        configuration.setCompetitions(Map.of(
                "premier-league", pair(2, 25544), "la-liga", pair(4, 25662),
                "serie-a", pair(5, 25518), "bundesliga", pair(3, 25666),
                "ligue-1", pair(22, 25554)));

        assertThat(configuration.getEndpoint()).isEqualTo("/statisticsfeed/1/getplayerstatistics");
        assertThat(configuration.getStatsAccumulationType()).isZero();
        assertThat(configuration.isCurrent()).isTrue();
        assertThat(configuration.getSortBy()).isEqualTo("Rating");
        assertThat(configuration.getField()).isEqualTo("Overall");
        assertThat(configuration.isMinApp()).isFalse();
        assertThat(configuration.getNumberOfPlayersToPick()).isEqualTo(500);
        assertThat(configuration.forCompetition(CompeticionEstadistica.PREMIER_LEAGUE)
                .getTournamentOptions()).isEqualTo(2);
        assertThat(configuration.forCompetition(CompeticionEstadistica.LIGUE_1).getStageId())
                .isEqualTo(25554);
    }

    @Test
    void rechazaCompeticionSinParejaConfigurada() {
        var configuration = new WhoScoredStatisticsConfiguration();
        assertThatThrownBy(() -> configuration.forCompetition(CompeticionEstadistica.PREMIER_LEAGUE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enlazaLasPropiedadesInternasSinExponerlasAlDominio() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfiguration.class)
                .withPropertyValues(
                        "whoscored.statistics.endpoint=/statisticsfeed/1/getplayerstatistics",
                        "whoscored.statistics.stats-accumulation-type=0",
                        "whoscored.statistics.current=true",
                        "whoscored.statistics.sort-by=Rating",
                        "whoscored.statistics.field=Overall",
                        "whoscored.statistics.min-app=false",
                        "whoscored.statistics.number-of-players-to-pick=500",
                        "whoscored.statistics.competitions.premier-league.tournament-options=2",
                        "whoscored.statistics.competitions.premier-league.stage-id=25544")
                .run(context -> {
                    var configuration = context.getBean(WhoScoredStatisticsConfiguration.class);
                    assertThat(configuration.forCompetition(CompeticionEstadistica.PREMIER_LEAGUE)
                            .getStageId()).isEqualTo(25544);
                    assertThat(configuration.getNumberOfPlayersToPick()).isEqualTo(500);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(WhoScoredStatisticsConfiguration.class)
    static class TestConfiguration { }

    private static WhoScoredStatisticsConfiguration.CompetitionParameters pair(int tournamentOptions,
            int stageId) {
        var parameters = new WhoScoredStatisticsConfiguration.CompetitionParameters();
        parameters.setTournamentOptions(tournamentOptions);
        parameters.setStageId(stageId);
        return parameters;
    }
}
