package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.IdentidadEstadisticaJugador;
import com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.IdentidadExternaJugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.service.exception.IdentidadEstadisticaNoResueltaException;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Resuelve jugadores sólo dentro del equipo local previamente resuelto. */
@Service
public class IdentidadJugadorResolver {
    private static final double FUZZY_MINIMO = 0.85;
    private static final double FUZZY_MARGEN = 0.15;
    private final JugadorRepository jugadores;
    private final IdentidadExternaJugadorRepository identidades;

    public IdentidadJugadorResolver(JugadorRepository jugadores, IdentidadExternaJugadorRepository identidades) {
        this.jugadores = jugadores;
        this.identidades = identidades;
    }

    public Resultado resolver(IdentidadEstadisticaJugador hint, EquipoEntity equipo,
            CompeticionEstadistica competicion) {
        var persisted = identidades.findByProveedorAndExternalId(ProveedorIdentidadExterna.WHOSCORED, hint.playerId());
        if (persisted.isPresent()) {
            var jugador = persisted.get().getJugador();
            if (equipo.getId().equals(jugador.getEquipoId())) {
                return new Resultado(jugador.getId(), null, equipo.getId(), null);
            }
            throw noResuelto(hint, equipo, competicion, List.of(jugador),
                    IdentidadEstadisticaNoResueltaException.Motivo.EVIDENCIA_INCOMPATIBLE);
        }
        List<JugadorEntity> candidatos = jugadores.findAllByEquipoIdAndActivoTrue(equipo.getId()).stream()
                .filter(j -> compatibleSiInformado(j.getPosicion(), hint.posicion()))
                .filter(j -> compatibleSiInformado(j.getNacionalidad(), hint.nacionalidad())).toList();
        String nombre = normalizar(hint.nombre());
        List<JugadorEntity> exactos = candidatos.stream().filter(j -> normalizar(j.getNombre()).equals(nombre)).toList();
        if (exactos.size() == 1) return aceptar(exactos.get(0), hint, equipo);
        if (exactos.size() > 1) throw noResuelto(hint, equipo, competicion, exactos,
                IdentidadEstadisticaNoResueltaException.Motivo.AMBIGUEDAD);
        List<JugadorEntity> estructurales = candidatos.stream()
                .filter(j -> compatibleEstructural(nombre, normalizar(j.getNombre()))).toList();
        if (estructurales.size() == 1) return aceptar(estructurales.get(0), hint, equipo);
        if (estructurales.size() > 1) throw noResuelto(hint, equipo, competicion, estructurales,
                IdentidadEstadisticaNoResueltaException.Motivo.AMBIGUEDAD);
        List<Scored> fuzzy = candidatos.stream().map(j -> new Scored(j, similitud(nombre, normalizar(j.getNombre()))))
                .sorted(Comparator.comparingDouble(Scored::score).reversed()
                        .thenComparing(s -> normalizar(s.jugador().getNombre()))
                        .thenComparing(s -> s.jugador().getId())).toList();
        if (!fuzzy.isEmpty() && fuzzy.get(0).score() >= FUZZY_MINIMO
                && (fuzzy.size() == 1 || fuzzy.get(0).score() - fuzzy.get(1).score() >= FUZZY_MARGEN)) {
            return aceptar(fuzzy.get(0).jugador(), hint, equipo);
        }
        throw noResuelto(hint, equipo, competicion, fuzzy.stream().limit(2).map(Scored::jugador).toList(),
                fuzzy.isEmpty() ? IdentidadEstadisticaNoResueltaException.Motivo.SIN_CANDIDATOS
                        : IdentidadEstadisticaNoResueltaException.Motivo.EVIDENCIA_INSUFICIENTE);
    }

    private Resultado aceptar(JugadorEntity jugador, IdentidadEstadisticaJugador hint, EquipoEntity equipo) {
        return new Resultado(jugador.getId(), null, equipo.getId(),
                new NuevaIdentidad(jugador.getId(), hint.playerId(), jugador));
    }

    private static boolean compatibleEstructural(String externo, String local) {
        List<String> a = tokens(externo);
        List<String> b = tokens(local);
        CommonRun run = tramoComun(a, b);
        if (run.length() == 0) return false;
        String apellido = a.get(run.leftStart());
        if (run.length() == 1 && (tokenGenerico(apellido)
                || run.leftStart() == 0 && run.rightStart() == 0)) return false;
        List<String> nombreA = a.subList(0, run.leftStart());
        List<String> nombreB = b.subList(0, run.rightStart());
        if (nombreA.isEmpty() || nombreB.isEmpty()) return run.length() >= 2;
        return nombreCompatible(nombreA, nombreB);
    }

    private static CommonRun tramoComun(List<String> a, List<String> b) {
        CommonRun best = new CommonRun(0, 0, 0);
        for (int i = 0; i < a.size(); i++) for (int j = 0; j < b.size(); j++) {
            int n = 0;
            while (i + n < a.size() && j + n < b.size() && a.get(i + n).equals(b.get(j + n))) n++;
            if (n > best.length() || (n == best.length() && i > best.leftStart())) best = new CommonRun(i, j, n);
        }
        return best;
    }

    private static boolean nombreCompatible(List<String> a, List<String> b) {
        String primeroA = a.get(0);
        String primeroB = b.get(0);
        if (primeroA.equals(primeroB)) return true;
        int min = Math.min(primeroA.length(), primeroB.length());
        if (min >= 3 && (primeroA.startsWith(primeroB) || primeroB.startsWith(primeroA))) return true;
        return min >= 3 && lcs(primeroA, primeroB) / (double) min >= 0.75;
    }

    private static boolean tokenGenerico(String token) { return token.length() <= 3; }

    private static List<String> tokens(String value) {
        String canonical = normalizar(value);
        return canonical.isEmpty() ? List.of() : List.of(canonical.split(" "));
    }

    private static double similitud(String a, String b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        return 1.0 - distancia(a, b) / (double) Math.max(a.length(), b.length());
    }

    private static int distancia(String a, String b) {
        int[] prev = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int[] next = new int[b.length() + 1]; next[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                next[j] = Math.min(Math.min(next[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            }
            prev = next;
        }
        return prev[b.length()];
    }

    private static int lcs(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 1; i <= a.length(); i++) for (int j = 1; j <= b.length(); j++)
            dp[i][j] = a.charAt(i - 1) == b.charAt(j - 1)
                    ? dp[i - 1][j - 1] + 1 : Math.max(dp[i - 1][j], dp[i][j - 1]);
        return dp[a.length()][b.length()];
    }

    private static boolean compatibleSiInformado(String local, String externo) {
        return externo == null || (local != null && normalizar(local).equals(normalizar(externo)));
    }

    /** NFKD, sin marcas, lowercase ROOT, apostrofes fuera, guiones como espacios y puntuación fuera. */
    static String normalizar(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
                .replaceAll("[\\u0027\\u2019\\u02BC]", "")
                .replaceAll("\\p{Pd}", " ").replaceAll("[\\p{P}\\p{S}]", " ")
                .replaceAll("[\\s\\p{Z}]+", " ").trim();
    }

    private static IdentidadEstadisticaNoResueltaException noResuelto(IdentidadEstadisticaJugador hint,
            EquipoEntity equipo, CompeticionEstadistica competicion, List<JugadorEntity> candidatos,
            IdentidadEstadisticaNoResueltaException.Motivo motivo) {
        var ordenados = candidatos.stream().sorted(Comparator.comparing(JugadorEntity::getNombre)
                .thenComparing(JugadorEntity::getId)).toList();
        return new IdentidadEstadisticaNoResueltaException(new IdentidadEstadisticaNoResueltaException.Diagnostico(
                IdentidadEstadisticaNoResueltaException.Tipo.JUGADOR, "WHOSCORED", hint.playerId(), hint.nombre(),
                competicion.name(), hint.nombreEquipo(), equipo.getNombre(), candidato(ordenados, 0, hint),
                candidato(ordenados, 1, hint), motivo));
    }

    private static IdentidadEstadisticaNoResueltaException.Candidato candidato(List<JugadorEntity> candidatos,
            int indice, IdentidadEstadisticaJugador hint) {
        if (indice >= candidatos.size()) return null;
        var j = candidatos.get(indice);
        return new IdentidadEstadisticaNoResueltaException.Candidato(j.getId().toString(), j.getNombre(),
                similitud(normalizar(hint.nombre()), normalizar(j.getNombre())));
    }

    public record Resultado(UUID jugadorId, IdentidadEquipoResolver.NuevaIdentidad equipoNueva,
            UUID equipoId, NuevaIdentidad identidadNueva) { }
    public record NuevaIdentidad(UUID jugadorId, long externalId, JugadorEntity jugador) { }
    private record Scored(JugadorEntity jugador, double score) { }
    private record CommonRun(int leftStart, int rightStart, int length) { }
}
