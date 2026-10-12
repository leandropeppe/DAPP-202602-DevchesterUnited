package com.devchesterunited.devchesterunitedv2.diagnostico;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parses and validates candidate enrollment mappings before human approval. */
public final class WhoscoredTeamEnrollmentApprovedCsv {
    public static final int EXPECTED_TEAM_COUNT = 96;
    public static final String OUTPUT_HEADER = "whoscored_team_id,football_data_external_id";

    private static final Map<Long, Long> MANUAL_CORRECTIONS = Map.of(
            63L, 78L,
            309L, 546L,
            228L, 523L);

    private WhoscoredTeamEnrollmentApprovedCsv() { }

    static List<Mapping> transform(List<Row> sourceRows) {
        List<Mapping> mappings = sourceRows.stream()
                .map(row -> new Mapping(row.whoscoredTeamId(),
                        MANUAL_CORRECTIONS.getOrDefault(row.whoscoredTeamId(), row.candidateFootballDataId())))
                .toList();
        validate(sourceRows, mappings);
        return mappings;
    }

    static void validate(List<Row> sourceRows, List<Mapping> mappings) {
        if (mappings.size() != EXPECTED_TEAM_COUNT) {
            throw new IllegalArgumentException("Se esperaban exactamente 96 mappings: " + mappings.size());
        }
        Set<Long> sourceIds = sourceRows.stream().map(Row::whoscoredTeamId)
                .collect(HashSet::new, Set::add, Set::addAll);
        Set<Long> mappingWhoscoredIds = mappings.stream().map(Mapping::whoscoredTeamId)
                .collect(HashSet::new, Set::add, Set::addAll);
        Set<Long> footballDataIds = mappings.stream().map(Mapping::footballDataExternalId)
                .collect(HashSet::new, Set::add, Set::addAll);
        if (sourceRows.size() != EXPECTED_TEAM_COUNT || sourceIds.size() != EXPECTED_TEAM_COUNT
                || mappingWhoscoredIds.size() != EXPECTED_TEAM_COUNT) {
            throw new IllegalArgumentException("Los IDs WhoScored de origen no son exactamente 96 y unicos");
        }
        if (!sourceIds.equals(mappingWhoscoredIds)) {
            throw new IllegalArgumentException("Los mappings no contienen exactamente los IDs WhoScored de origen");
        }
        if (footballDataIds.size() != EXPECTED_TEAM_COUNT) {
            throw new IllegalArgumentException("Los IDs Football-Data no son exactamente 96 y unicos");
        }
        if (mappings.stream().anyMatch(mapping -> mapping.whoscoredTeamId() == null
                || mapping.footballDataExternalId() == null
                || mapping.whoscoredTeamId() <= 0 || mapping.footballDataExternalId() <= 0)) {
            throw new IllegalArgumentException("No se permiten IDs vacios o menores/iguales a cero");
        }
    }

    record Row(Long whoscoredTeamId, Long candidateFootballDataId) { }
    record Mapping(Long whoscoredTeamId, Long footballDataExternalId) { }
}
