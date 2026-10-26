package com.devchesterunited.devchesterunitedv2.scraping.catalogo;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WhoscoredTeamEnrollmentApprovedCsvTest {
    @Test
    void acepta96MappingsValidos() {
        assertEquals(96, WhoscoredTeamEnrollmentApprovedCsv.transform(validRows()).size());
    }

    @Test
    void fallaConDuplicateWhoScoredId() {
        List<WhoscoredTeamEnrollmentApprovedCsv.Row> rows = validRows();
        rows.set(1, new WhoscoredTeamEnrollmentApprovedCsv.Row(1L, 1001L));
        assertThrows(IllegalArgumentException.class, () -> WhoscoredTeamEnrollmentApprovedCsv.transform(rows));
    }

    @Test
    void fallaConDuplicateFootballDataId() {
        List<WhoscoredTeamEnrollmentApprovedCsv.Row> rows = validRows();
        rows.set(1, new WhoscoredTeamEnrollmentApprovedCsv.Row(2L, 1001L));
        assertThrows(IllegalArgumentException.class, () -> WhoscoredTeamEnrollmentApprovedCsv.transform(rows));
    }

    @Test
    void fallaConMappingVacio() {
        List<WhoscoredTeamEnrollmentApprovedCsv.Row> rows = validRows();
        rows.set(1, new WhoscoredTeamEnrollmentApprovedCsv.Row(2L, null));
        assertThrows(IllegalArgumentException.class, () -> WhoscoredTeamEnrollmentApprovedCsv.transform(rows));
    }

    @Test
    void aplicaExactamenteLasTresCorreccionesManuales() {
        var mappings = WhoscoredTeamEnrollmentApprovedCsv.transform(validRows());
        assertEquals(new WhoscoredTeamEnrollmentApprovedCsv.Mapping(63L, 78L), mappings.stream()
                .filter(mapping -> mapping.whoscoredTeamId().equals(63L)).findFirst().orElseThrow());
        assertEquals(new WhoscoredTeamEnrollmentApprovedCsv.Mapping(309L, 546L), mappings.stream()
                .filter(mapping -> mapping.whoscoredTeamId().equals(309L)).findFirst().orElseThrow());
        assertEquals(new WhoscoredTeamEnrollmentApprovedCsv.Mapping(228L, 523L), mappings.stream()
                .filter(mapping -> mapping.whoscoredTeamId().equals(228L)).findFirst().orElseThrow());
    }

    private static List<WhoscoredTeamEnrollmentApprovedCsv.Row> validRows() {
        List<WhoscoredTeamEnrollmentApprovedCsv.Row> rows = new ArrayList<>();
        for (long id = 1; id <= 93; id++) rows.add(new WhoscoredTeamEnrollmentApprovedCsv.Row(id, id + 1000));
        rows.add(new WhoscoredTeamEnrollmentApprovedCsv.Row(227L, 1227L));
        rows.add(new WhoscoredTeamEnrollmentApprovedCsv.Row(309L, 1309L));
        rows.add(new WhoscoredTeamEnrollmentApprovedCsv.Row(228L, 1228L));
        return rows;
    }
}
