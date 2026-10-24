package com.devchesterunited.devchesterunitedv2.adapter.dto;
import com.devchesterunited.devchesterunitedv2.scraping.estadisticas.WhoScoredStatisticsFeedResponseDTO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WhoScoredCompleteStatisticsParsingTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parseaLasCuatroFamiliasDelFixtureCompleto() throws Exception {
        for (String fixture : new String[] { "summary-complete.json", "offensive-complete.json",
                "defensive-complete.json", "summary-passing.json" }) {
            String json = Files.readString(Path.of("src/test/resources/fixtures/whoscored", fixture));
            var response = mapper.readValue(json, WhoScoredStatisticsFeedResponseDTO.class);
            assertNotNull(response.paging());
            assertEquals(1, response.currentPage());
            assertEquals(1, response.totalPages());
            assertEquals(response.totalResults(), response.players().size());
            assertNotNull(response.players().get(0).playerId());
        }
    }
}
