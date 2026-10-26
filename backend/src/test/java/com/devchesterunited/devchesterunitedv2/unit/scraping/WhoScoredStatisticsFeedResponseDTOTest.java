package com.devchesterunited.devchesterunitedv2.unit.scraping;

import com.devchesterunited.devchesterunitedv2.scraping.estadisticas.WhoScoredStatisticsFeedResponseDTO;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class WhoScoredStatisticsFeedResponseDTOTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializaLasTresFamiliasRealesConElSobreComun() throws IOException {
        var summary = leer("summary-all.json");
        var offensive = leer("summary-offensive.json");
        var defensive = leer("summary-defensive.json");

        assertThat(summary.currentPage()).isEqualTo(1);
        assertThat(summary.totalPages()).isEqualTo(1);
        assertThat(summary.resultsPerPage()).isEqualTo(500);
        assertThat(summary.totalResults()).isEqualTo(1);
        assertThat(summary.paging()).isNotNull();
        assertThat(summary.playerTableStats()).hasSize(1);
        assertThat(summary.players()).singleElement().satisfies(player -> {
            assertThat(player.playerId()).isEqualTo(101);
            assertThat(player.minsPlayed()).isEqualTo(900);
            assertThat(player.goal()).isEqualTo(8);
            assertThat(player.rating()).hasToString("7.2");
        });
        assertThat(offensive.players()).singleElement().satisfies(player -> {
            assertThat(player.keyPassPerGame()).hasToString("1.7");
            assertThat(player.dribbleWonPerGame()).hasToString("2.1");
        });
        assertThat(defensive.players()).singleElement()
                .extracting(WhoScoredStatisticsFeedResponseDTO.PlayerDTO::tacklePerGame)
                .hasToString("1.9");
    }

    private WhoScoredStatisticsFeedResponseDTO leer(String fixture) throws IOException {
        String path = "/fixtures/whoscored/" + fixture;
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("fixture %s", fixture).isNotNull();
            return objectMapper.readValue(input, WhoScoredStatisticsFeedResponseDTO.class);
        }
    }
}
