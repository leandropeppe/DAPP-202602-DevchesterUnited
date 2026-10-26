package com.devchesterunited.devchesterunitedv2.scraping.catalogo;

import com.devchesterunited.devchesterunitedv2.scraping.estadisticas.ProveedorEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.IdentidadEstadisticaJugador;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.RegistroEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.SnapshotEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.EquipoEntity;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Reporte humano de enrolamiento de equipos WhoScored. No persiste asociaciones. */
@Service
public class WhoscoredTeamEnrollmentReportService {
    public static final List<CompeticionEstadistica> COMPETICIONES = List.of(
            CompeticionEstadistica.PREMIER_LEAGUE, CompeticionEstadistica.LA_LIGA,
            CompeticionEstadistica.SERIE_A, CompeticionEstadistica.BUNDESLIGA,
            CompeticionEstadistica.LIGUE_1);

    private static final List<String> HEADERS = List.of(
            "competicion", "whoscored_team_id", "whoscored_team_name", "jugadores_observados",
            "candidato_1_uuid", "candidato_1_football_data_id", "candidato_1_nombre",
            "candidato_1_nombre_corto", "candidato_1_sigla", "candidato_1_score",
            "candidato_1_motivo", "candidato_2_uuid", "candidato_2_football_data_id",
            "candidato_2_nombre", "candidato_2_nombre_corto", "candidato_2_sigla",
            "candidato_2_score", "candidato_2_motivo", "decision_manual",
            "football_data_id_aprobado");

    private final ProveedorEstadisticasJugador proveedor;
    private final EquipoRepository equipos;
    private final IdentidadExternaEquipoRepository identidades;

    public WhoscoredTeamEnrollmentReportService(ProveedorEstadisticasJugador proveedor,
            EquipoRepository equipos, IdentidadExternaEquipoRepository identidades) {
        this.proveedor = Objects.requireNonNull(proveedor);
        this.equipos = Objects.requireNonNull(equipos);
        this.identidades = Objects.requireNonNull(identidades);
    }

    public ReportResult generar(Path output) throws IOException {
        List<SnapshotEstadisticasJugador> snapshots = proveedor.obtenerSnapshots(COMPETICIONES);
        List<ObservedTeam> observed = deduplicate(snapshots);
        Map<CompeticionEstadistica, List<EquipoEntity>> localByCompetition = new EnumMap<>(CompeticionEstadistica.class);
        for (CompeticionEstadistica competition : COMPETICIONES) {
            localByCompetition.put(competition, equipos.findAllByActivoTrueAndLigaIgnoreCase(ligaDe(competition)));
        }
        Map<UUID, Long> footballDataIds = identidades.findAllByProveedor(ProveedorIdentidadExterna.FOOTBALL_DATA)
                .stream().collect(Collectors.toMap(identity -> identity.getEquipo().getId(),
                        identity -> identity.getExternalId(), (left, right) -> left));
        Set<Long> whoscoredIds = identidades.findAllByProveedor(ProveedorIdentidadExterna.WHOSCORED)
                .stream().map(identity -> identity.getExternalId()).collect(Collectors.toSet());

        List<EnrollmentReportRow> rows = construirTodasLasFilas(observed, localByCompetition, footballDataIds);
        escribirCsv(rows, output);

        Map<CompeticionEstadistica, Long> byCompetition = observed.stream()
                .collect(Collectors.groupingBy(ObservedTeam::competicion, () -> new EnumMap<>(CompeticionEstadistica.class), Collectors.counting()));
        for (CompeticionEstadistica competition : COMPETICIONES) byCompetition.putIfAbsent(competition, 0L);
        long withoutCandidates = rows.stream().filter(row -> row.candidates().isEmpty()).count();
        long withWhoScoredIdentity = observed.stream().filter(team -> whoscoredIds.contains(team.teamId())).count();
        return new ReportResult(output, observed.size(), byCompetition, withoutCandidates, withWhoScoredIdentity);
    }

    /** Calcula todas las filas antes de crear o reemplazar el archivo de salida. */
    List<EnrollmentReportRow> construirTodasLasFilas(List<ObservedTeam> observed,
            Map<CompeticionEstadistica, List<EquipoEntity>> localByCompetition,
            Map<UUID, Long> footballDataIds) {
        return observed.stream().map(team -> new EnrollmentReportRow(team,
                rankCandidatesForReport(team, localByCompetition.get(team.competicion()), footballDataIds))).toList();
    }

    /** Punto de extensión de tests; la implementación real solo ordena, nunca persiste. */
    protected List<Candidate> rankCandidatesForReport(ObservedTeam observed, List<EquipoEntity> localTeams,
            Map<UUID, Long> footballDataIds) {
        return rankCandidates(observed, localTeams, footballDataIds);
    }

    /** Escribe primero en un temporal y publica el archivo solo después de cerrar el writer. */
    void escribirCsv(List<EnrollmentReportRow> rows, Path output) throws IOException {
        Path absoluteOutput = output.toAbsolutePath();
        Path directory = absoluteOutput.getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, absoluteOutput.getFileName().toString(), ".tmp");
        boolean moved = false;
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write(csvLine(HEADERS));
                writer.newLine();
                for (EnrollmentReportRow row : rows) {
                    writer.write(csvLine(toCsvValues(row)));
                    writer.newLine();
                }
            }
            try {
                Files.move(temporary, absoluteOutput, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, absoluteOutput, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    private static List<String> toCsvValues(EnrollmentReportRow reportRow) {
        ObservedTeam team = reportRow.team();
        List<Candidate> candidates = reportRow.candidates();
        Candidate one = !candidates.isEmpty() ? candidates.get(0) : null;
        Candidate two = candidates.size() > 1 ? candidates.get(1) : null;
        return List.of(team.competicion().name(), String.valueOf(team.teamId()), nullToEmpty(team.teamName()),
                String.valueOf(team.jugadoresObservados()), value(one, Candidate::uuid), value(one, Candidate::footballDataId),
                value(one, Candidate::nombre), value(one, Candidate::nombreCorto), value(one, Candidate::sigla),
                value(one, Candidate::score), value(one, Candidate::motivo), value(two, Candidate::uuid),
                value(two, Candidate::footballDataId), value(two, Candidate::nombre), value(two, Candidate::nombreCorto),
                value(two, Candidate::sigla), value(two, Candidate::score), value(two, Candidate::motivo), "", "");
    }

    /** Deduplica por competencia y teamId, contando jugadores distintos. */
    public static List<ObservedTeam> deduplicate(Collection<SnapshotEstadisticasJugador> snapshots) {
        Map<Key, MutableObservedTeam> teams = new LinkedHashMap<>();
        for (SnapshotEstadisticasJugador snapshot : snapshots) {
            for (RegistroEstadisticasJugador registro : snapshot.registros()) {
                IdentidadEstadisticaJugador identity = registro.identidad();
                if (identity.teamId() == null) continue;
                Key key = new Key(snapshot.competicion(), identity.teamId());
                MutableObservedTeam team = teams.computeIfAbsent(key,
                        ignored -> new MutableObservedTeam(snapshot.competicion(), identity.teamId(), identity.nombreEquipo()));
                team.teamName = firstNonBlank(team.teamName, identity.nombreEquipo());
                team.playerIds.add(identity.playerId());
            }
        }
        return teams.values().stream().map(MutableObservedTeam::toImmutable).toList();
    }

    /** Ordena evidencia diagnóstica; nunca decide ni persiste una asociación. */
    public static List<Candidate> rankCandidates(ObservedTeam observed, List<EquipoEntity> localTeams,
            Map<UUID, Long> footballDataIds) {
        if (localTeams == null) return List.of();
        return localTeams.stream().map(team -> score(observed.teamName(), team, footballDataIds.get(team.getId())))
                .sorted(Comparator.comparing(Candidate::score).reversed()
                        .thenComparing(candidate -> nullToEmpty(candidate.nombre()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(candidate -> candidate.uuid().toString()))
                .limit(2).toList();
    }

    public static String ligaDe(CompeticionEstadistica competition) {
        return switch (competition) {
            case PREMIER_LEAGUE -> "Premier League";
            case LA_LIGA -> "LaLiga";
            case SERIE_A -> "Serie A";
            case BUNDESLIGA -> "Bundesliga";
            case LIGUE_1 -> "Ligue 1";
        };
    }

    private static Candidate score(String externalName, EquipoEntity team, Long footballDataId) {
        String external = canonical(externalName);
        String name = canonical(team.getNombre());
        String shortName = canonical(team.getNombreCorto());
        String acronym = canonical(team.getSigla());
        double similarity = similarity(external, name);
        double score = similarity;
        List<String> reasons = new ArrayList<>();
        if (!external.isEmpty() && external.equals(name)) { score = 1.0; reasons.add("nombre_exacto"); }
        if (!external.isEmpty() && external.equals(shortName)) { score = Math.max(score, 0.95); reasons.add("nombre_corto_exacto"); }
        if (!external.isEmpty() && external.equals(acronym)) { score = Math.max(score, 0.90); reasons.add("sigla_exacta"); }
        if (similarity > 0.0 && reasons.stream().noneMatch(reason -> reason.equals("nombre_exacto"))) {
            reasons.add("similitud_nombre");
        }
        return new Candidate(team.getId(), footballDataId, team.getNombre(), team.getNombreCorto(), team.getSigla(),
                score, String.join("+", reasons));
    }

    private static <T> String value(Candidate candidate, java.util.function.Function<Candidate, T> getter) {
        return candidate == null || getter.apply(candidate) == null ? "" : String.valueOf(getter.apply(candidate));
    }

    private static String csvLine(List<String> values) {
        return values.stream().map(WhoscoredTeamEnrollmentReportService::csv).collect(Collectors.joining(","));
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    static String canonical(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private static double similarity(String left, String right) {
        if (left.isEmpty() || right.isEmpty()) return 0.0;
        int distance = levenshtein(left, right);
        return Math.max(0.0, 1.0 - ((double) distance / Math.max(left.length(), right.length())));
    }

    private static int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1]; current[0] = i;
            for (int j = 1; j <= right.length(); j++) current[j] = Math.min(Math.min(current[j - 1] + 1,
                    previous[j] + 1), previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1));
            previous = current;
        }
        return previous[right.length()];
    }

    private static String firstNonBlank(String current, String candidate) {
        return current == null || current.isBlank() ? candidate : current;
    }

    private static String nullToEmpty(String value) { return value == null ? "" : value; }

    public record ObservedTeam(CompeticionEstadistica competicion, long teamId, String teamName,
            int jugadoresObservados) { }

    public record Candidate(UUID uuid, Long footballDataId, String nombre, String nombreCorto,
            String sigla, double score, String motivo) { }

    public record EnrollmentReportRow(ObservedTeam team, List<Candidate> candidates) {
        public EnrollmentReportRow {
            Objects.requireNonNull(team, "El equipo observado es obligatorio.");
            candidates = List.copyOf(Objects.requireNonNull(candidates, "Los candidatos son obligatorios."));
        }
    }

    public record ReportResult(Path output, int totalTeams, Map<CompeticionEstadistica, Long> byCompetition,
            long withoutCandidates, long withWhoScoredIdentity) { }

    private record Key(CompeticionEstadistica competition, long teamId) { }

    private static final class MutableObservedTeam {
        private final CompeticionEstadistica competition;
        private final long teamId;
        private String teamName;
        private final Set<Long> playerIds = new HashSet<>();
        private MutableObservedTeam(CompeticionEstadistica competition, long teamId, String teamName) {
            this.competition = competition; this.teamId = teamId; this.teamName = teamName;
        }
        private ObservedTeam toImmutable() { return new ObservedTeam(competition, teamId, teamName, playerIds.size()); }
    }
}
