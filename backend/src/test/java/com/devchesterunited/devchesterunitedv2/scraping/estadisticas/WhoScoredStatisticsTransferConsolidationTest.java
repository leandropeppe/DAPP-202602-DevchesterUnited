package com.devchesterunited.devchesterunitedv2.scraping.estadisticas;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.FamiliaEstadistica;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhoScoredStatisticsTransferConsolidationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void consolidaTransferenciaYCalculaRatingPonderado() throws Exception {
        var snapshot = new WhoScoredStatisticsSnapshotAssembler().ensamblar(
                CompeticionEstadistica.PREMIER_LEAGUE, respuestas(), true);
        var registro = snapshot.registros().get(0);
        var metricas = registro.metricas();

        assertThat(snapshot.registros()).hasSize(1);
        assertThat(registro.identidad().teamId()).isNull();
        assertThat(metricas.minutosJugados()).isEqualTo(771);
        assertThat(metricas.generales().apps()).isEqualTo(10);
        assertThat(metricas.goles()).isZero();
        assertThat(metricas.asistencias()).isEqualTo(3);
        assertThat(metricas.valoracion()).isEqualByComparingTo("6.98");
        assertThat(metricas.tiros()).isEqualByComparingTo("1.70");
    }

    @Test
    void rechazaTemporadasIncompatiblesAunqueElJugadorSeaElMismo() throws Exception {
        var respuestas = respuestas(25545, 25544);

        assertThatThrownBy(() -> new WhoScoredStatisticsSnapshotAssembler().ensamblar(
                CompeticionEstadistica.PREMIER_LEAGUE, respuestas, true))
                .isInstanceOf(WhoScoredStatisticsException.class)
                .hasMessageContaining("temporadas incompatibles");
    }

    private Map<FamiliaEstadistica, WhoScoredStatisticsFeedResponseDTO> respuestas() throws Exception {
        return respuestas(25544, 25544);
    }

    private Map<FamiliaEstadistica, WhoScoredStatisticsFeedResponseDTO> respuestas(long seasonId) throws Exception {
        return respuestas(seasonId, seasonId);
    }

    private Map<FamiliaEstadistica, WhoScoredStatisticsFeedResponseDTO> respuestas(
            long seasonId, long secondSeasonId) throws Exception {
        Map<FamiliaEstadistica, WhoScoredStatisticsFeedResponseDTO> resultado = new LinkedHashMap<>();
        resultado.put(FamiliaEstadistica.GENERAL, response(
                """
                {"playerId":366116,"name":"Jugador","teamId":31,"teamName":"Everton",
                 "minsPlayed":180,"apps":2,"goal":0,"assistTotal":1,"rating":7.385,
                 "seasonId":25544,"seasonName":"2025/26"}
                """.replace("25544", String.valueOf(seasonId)),
                """
                {"playerId":366116,"name":"Jugador","teamId":167,"teamName":"Man City",
                 "minsPlayed":591,"apps":8,"goal":0,"assistTotal":2,"rating":6.87625,
                 "seasonId":25544,"seasonName":"2025/26"}
                """.replace("25544", String.valueOf(secondSeasonId))));
        resultado.put(FamiliaEstadistica.OFENSIVA, response(
                """
                {"playerId":366116,"name":"Jugador","teamId":31,"teamName":"Everton","apps":2,"shotsPerGame":2.0}
                """,
                """
                {"playerId":366116,"name":"Jugador","teamId":167,"teamName":"Man City","apps":8,"shotsPerGame":1.625}
                """));
        resultado.put(FamiliaEstadistica.DEFENSIVA, response(
                """
                {"playerId":366116,"name":"Jugador","teamId":31,"teamName":"Everton","apps":2,"tacklePerGame":1.0}
                """,
                """
                {"playerId":366116,"name":"Jugador","teamId":167,"teamName":"Man City","apps":8,"tacklePerGame":2.0}
                """));
        resultado.put(FamiliaEstadistica.PASES, response(
                """
                {"playerId":366116,"name":"Jugador","teamId":31,"teamName":"Everton","totalPassesPerGame":40.0,"passSuccess":90.0}
                """,
                """
                {"playerId":366116,"name":"Jugador","teamId":167,"teamName":"Man City","totalPassesPerGame":50.0,"passSuccess":80.0}
                """));
        return resultado;
    }

    private WhoScoredStatisticsFeedResponseDTO response(String... players) throws Exception {
        String json = """
                {"paging":{"currentPage":1,"totalPages":1,"resultsPerPage":500,"totalResults":%d},
                 "playerTableStats":[%s]}
                """.formatted(players.length, String.join(",", players));
        return mapper.readValue(json, WhoScoredStatisticsFeedResponseDTO.class);
    }
}
