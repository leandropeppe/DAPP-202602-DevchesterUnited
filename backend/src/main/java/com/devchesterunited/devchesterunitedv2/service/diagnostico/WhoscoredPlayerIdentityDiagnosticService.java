package com.devchesterunited.devchesterunitedv2.service.diagnostico;

import com.devchesterunited.devchesterunitedv2.adapter.ProveedorEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.IdentidadEstadisticaJugador;
import com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.modelo.RegistroEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.SnapshotEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.persistence.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.IdentidadExternaJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorEntity;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Read-only diagnostic report; it never invokes synchronization, staging, or promotion. */
@Service
public class WhoscoredPlayerIdentityDiagnosticService {
    public static final List<CompeticionEstadistica> COMPETICIONES = List.of(
            CompeticionEstadistica.PREMIER_LEAGUE, CompeticionEstadistica.LA_LIGA,
            CompeticionEstadistica.SERIE_A, CompeticionEstadistica.BUNDESLIGA,
            CompeticionEstadistica.LIGUE_1);

    private static final List<String> HEADERS = List.of(
            "competition", "whoscored_team_id", "whoscored_team_name", "local_team_id", "local_team_name",
            "whoscored_player_id", "whoscored_player_name", "whoscored_position", "whoscored_nationality",
            "candidate_1_local_player_id", "candidate_1_football_data_external_id", "candidate_1_name",
            "candidate_1_position", "candidate_1_nationality", "candidate_1_exact_current",
            "candidate_1_diacritic_normalized_name", "candidate_1_diacritic_exact", "candidate_1_similarity",
            "candidate_2_local_player_id", "candidate_2_name", "candidate_2_similarity",
            "candidate_3_local_player_id", "candidate_3_name", "candidate_3_similarity", "candidate_count");

    private final ProveedorEstadisticasJugador proveedor;
    private final IdentidadExternaEquipoRepository identidadesEquipos;
    private final IdentidadExternaJugadorRepository identidadesJugadores;
    private final JugadorRepository jugadores;

    public WhoscoredPlayerIdentityDiagnosticService(ProveedorEstadisticasJugador proveedor,
            IdentidadExternaEquipoRepository identidadesEquipos,
            IdentidadExternaJugadorRepository identidadesJugadores, JugadorRepository jugadores) {
        this.proveedor = Objects.requireNonNull(proveedor);
        this.identidadesEquipos = Objects.requireNonNull(identidadesEquipos);
        this.identidadesJugadores = Objects.requireNonNull(identidadesJugadores);
        this.jugadores = Objects.requireNonNull(jugadores);
    }

    public ReportResult generar(Path output) throws IOException {
        List<SnapshotEstadisticasJugador> snapshots = proveedor.obtenerSnapshots(COMPETICIONES);
        List<ObservedPlayer> observed = deduplicate(snapshots);
        Map<Long, EquipoEntity> localTeams = resolveTeams(observed);
        Set<Long> persistedWhoScoredIds = identidadesJugadores.findAllByProveedor(ProveedorIdentidadExterna.WHOSCORED)
                .stream().map(identity -> identity.getExternalId()).collect(Collectors.toSet());
        Map<UUID, Long> footballDataIds = identidadesJugadores.findAllByProveedor(ProveedorIdentidadExterna.FOOTBALL_DATA)
                .stream().collect(Collectors.toMap(identity -> identity.getJugador().getId(),
                        identity -> identity.getExternalId(), (left, right) -> left));
        Map<UUID, List<JugadorEntity>> rosterByTeam = new HashMap<>();
        List<Row> rows = new ArrayList<>();
        for (ObservedPlayer player : observed) {
            if (!persistedWhoScoredIds.contains(player.identity().playerId())) {
                EquipoEntity team = localTeams.get(player.teamId());
                List<JugadorEntity> roster = rosterByTeam.computeIfAbsent(team.getId(),
                        jugadores::findAllByEquipoIdAndActivoTrue);
                List<JugadorEntity> currentCandidates = roster.stream()
                        .filter(candidate -> currentRuleMatches(candidate, player.identity()))
                        .toList();
                if (currentCandidates.size() != 1) {
                    List<Candidate> ranked = rankCandidates(player.identity(), roster, footballDataIds);
                    rows.add(new Row(player, team, ranked, currentCandidates.size()));
                }
            }
        }
        escribirCsv(rows, output);
        return new ReportResult(output, observed.size(), rows.size(), persistedWhoScoredIds.size());
    }

    private Map<Long, EquipoEntity> resolveTeams(List<ObservedPlayer> observed) {
        Map<Long, String> names = new LinkedHashMap<>();
        observed.forEach(player -> names.putIfAbsent(player.teamId(), player.teamName()));
        Map<Long, EquipoEntity> result = new LinkedHashMap<>();
        for (var entry : names.entrySet()) {
            var identity = identidadesEquipos.findByProveedorAndExternalId(
                    ProveedorIdentidadExterna.WHOSCORED, entry.getKey())
                    .orElseThrow(() -> new IllegalStateException("Falta identidad WHOSCORED de equipo: teamId="
                            + entry.getKey() + ", nombre=" + entry.getValue()));
            result.put(entry.getKey(), identity.getEquipo());
        }
        return result;
    }

    static List<Candidate> rankCandidates(IdentidadEstadisticaJugador external,
            Collection<JugadorEntity> roster, Map<UUID, Long> footballDataIds) {
        return roster.stream().map(candidate -> new Candidate(candidate,
                        footballDataIds.get(candidate.getId()), diagnosticSimilarity(external.nombre(), candidate.getNombre()),
                        currentRuleMatches(candidate, external)))
                .sorted(Comparator.comparing(Candidate::similarity).reversed()
                        .thenComparing(candidate -> nullToEmpty(candidate.player().getNombre()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(candidate -> candidate.player().getId().toString()))
                .limit(3).toList();
    }

    static boolean currentRuleMatches(JugadorEntity local, IdentidadEstadisticaJugador external) {
        return currentNormalize(local.getNombre()).equals(currentNormalize(external.nombre()))
                && compatible(local.getPosicion(), external.posicion())
                && compatible(local.getNacionalidad(), external.nacionalidad());
    }

    private static boolean compatible(String local, String external) {
        return external == null || (local != null && currentNormalize(local).equals(currentNormalize(external)));
    }

    public static String currentNormalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .trim().replaceAll("[\\s\\p{Z}]+", " ").toLowerCase(Locale.ROOT);
    }

    /** Diagnostic-only normalization; this is intentionally not used by IdentidadJugadorResolver. */
    public static String diagnosticNormalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("['’`´]", "")
                .replaceAll("[-‐‑‒–—―]", " ").replaceAll("[^\\p{Alnum}]+", " ")
                .trim().replaceAll("\\s+", " ");
    }

    public static double diagnosticSimilarity(String left, String right) {
        String a = diagnosticNormalize(left);
        String b = diagnosticNormalize(right);
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        return 1.0 - (double) levenshtein(a, b) / Math.max(a.length(), b.length());
    }

    private static int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1]; current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1));
            }
            previous = current;
        }
        return previous[right.length()];
    }

    static List<ObservedPlayer> deduplicate(Collection<SnapshotEstadisticasJugador> snapshots) {
        Map<Key, ObservedPlayer> result = new LinkedHashMap<>();
        for (SnapshotEstadisticasJugador snapshot : snapshots) {
            for (RegistroEstadisticasJugador registro : snapshot.registros()) {
                IdentidadEstadisticaJugador identity = registro.identidad();
                if (identity.teamId() == null) {
                    throw new IllegalStateException("El diagnóstico recibió un jugador sin teamId: playerId="
                            + identity.playerId());
                }
                result.putIfAbsent(new Key(identity.teamId(), identity.playerId()),
                        new ObservedPlayer(snapshot.competicion(), identity.teamId(), identity.nombreEquipo(), identity));
            }
        }
        return List.copyOf(result.values());
    }

    void escribirCsv(List<Row> rows, Path output) throws IOException {
        Path absolute = output.toAbsolutePath();
        Path directory = absolute.getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, absolute.getFileName().toString(), ".tmp");
        boolean moved = false;
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write(csvLine(HEADERS)); writer.newLine();
                for (Row row : rows) { writer.write(csvLine(values(row))); writer.newLine(); }
            }
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally { if (!moved) Files.deleteIfExists(temporary); }
    }

    private static List<String> values(Row row) {
        IdentidadEstadisticaJugador external = row.player().identity();
        Candidate one = at(row.candidates(), 0);
        Candidate two = at(row.candidates(), 1);
        Candidate three = at(row.candidates(), 2);
        return List.of(row.player().competition().name(), String.valueOf(row.player().teamId()), empty(row.player().teamName()),
                String.valueOf(row.team().getId()), empty(row.team().getNombre()), String.valueOf(external.playerId()),
                empty(external.nombre()), empty(external.posicion()), empty(external.nacionalidad()),
                value(one, c -> c.player().getId()), value(one, Candidate::footballDataId), value(one, c -> c.player().getNombre()),
                value(one, c -> c.player().getPosicion()), value(one, c -> c.player().getNacionalidad()),
                value(one, c -> c.currentRuleMatch()), value(one, c -> diagnosticNormalize(c.player().getNombre())),
                value(one, c -> diagnosticNormalize(c.player().getNombre()).equals(diagnosticNormalize(external.nombre()))),
                value(one, Candidate::similarity), value(two, c -> c.player().getId()), value(two, c -> c.player().getNombre()),
                value(two, Candidate::similarity), value(three, c -> c.player().getId()), value(three, c -> c.player().getNombre()),
                value(three, Candidate::similarity), String.valueOf(row.currentCandidateCount()));
    }

    private static Candidate at(List<Candidate> candidates, int index) { return index < candidates.size() ? candidates.get(index) : null; }
    private static <T> String value(Candidate candidate, Function<Candidate, T> getter) {
        return candidate == null || getter.apply(candidate) == null ? "" : String.valueOf(getter.apply(candidate));
    }
    private static String csvLine(List<String> values) { return values.stream().map(WhoscoredPlayerIdentityDiagnosticService::csv).collect(Collectors.joining(",")); }
    private static String csv(String value) { return "\"" + empty(value).replace("\"", "\"\"") + "\""; }
    private static String empty(String value) { return value == null ? "" : value; }
    private static String nullToEmpty(String value) { return value == null ? "" : value; }

    public record ReportResult(Path output, int observedPlayers, int unresolvedPlayers, int persistedPlayers) { }
    record ObservedPlayer(CompeticionEstadistica competition, long teamId, String teamName, IdentidadEstadisticaJugador identity) { }
    record Candidate(JugadorEntity player, Long footballDataId, double similarity, boolean currentRuleMatch) { }
    record Row(ObservedPlayer player, EquipoEntity team, List<Candidate> candidates, int currentCandidateCount) { }
    private record Key(long teamId, long playerId) { }
}
