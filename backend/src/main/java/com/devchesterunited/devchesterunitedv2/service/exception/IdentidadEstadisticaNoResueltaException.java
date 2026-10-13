package com.devchesterunited.devchesterunitedv2.service.exception;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Indica que una identidad externa no tiene una asociación local inequívoca. */
public class IdentidadEstadisticaNoResueltaException extends RuntimeException {
    public enum Tipo { EQUIPO, JUGADOR }

    public enum Motivo {
        EVIDENCIA_INSUFICIENTE, SIN_CANDIDATOS, THRESHOLD_INSUFICIENTE,
        MARGEN_INSUFICIENTE, AMBIGUEDAD, EVIDENCIA_INCOMPATIBLE,
          EQUIPO_NO_RESUELTO, EQUIPO_INACTIVO
    }

    public record Candidato(String id, String nombre, Double score) { }

    /** Diagnóstico técnico seguro; no contiene URLs, credenciales ni cuerpos del proveedor. */
    public record Diagnostico(
            Tipo tipo, String proveedor, Long externalId, String nombre, String competicion,
            String equipoWhoScored, String equipoLocal, Candidato mejorCandidato,
            Candidato segundoCandidato, Motivo motivo) {
        public Diagnostico {
            Objects.requireNonNull(tipo, "tipo");
            Objects.requireNonNull(motivo, "motivo");
        }
    }

    private final List<Diagnostico> diagnosticos;

    public IdentidadEstadisticaNoResueltaException(String message) {
        this(message, List.of());
    }

    public IdentidadEstadisticaNoResueltaException(Diagnostico diagnostico) {
        this(List.of(diagnostico));
    }

    public IdentidadEstadisticaNoResueltaException(List<Diagnostico> diagnosticos) {
        this(resumen(diagnosticos), diagnosticos);
    }

    private IdentidadEstadisticaNoResueltaException(String message, List<Diagnostico> diagnosticos) {
        super(message);
        this.diagnosticos = List.copyOf(diagnosticos);
    }

    public List<Diagnostico> diagnosticos() {
        return diagnosticos;
    }

    private static String resumen(List<Diagnostico> diagnosticos) {
        long equipos = diagnosticos.stream().filter(d -> d.tipo() == Tipo.EQUIPO).count();
        long jugadores = diagnosticos.stream().filter(d -> d.tipo() == Tipo.JUGADOR).count();
        Map<Motivo, Long> porMotivo = diagnosticos.stream().collect(Collectors.groupingBy(
                Diagnostico::motivo, java.util.LinkedHashMap::new, Collectors.counting()));

        StringBuilder resumen = new StringBuilder("No fue posible resolver ")
                .append(diagnosticos.size()).append(" identidad(es) estadística(s). ")
                .append("EQUIPO=").append(equipos).append(", JUGADOR=").append(jugadores).append(". ")
                .append("Motivos: ");
        porMotivo.forEach((motivo, cantidad) -> resumen.append(motivo).append("=")
                .append(cantidad).append(", "));
        if (!porMotivo.isEmpty()) resumen.setLength(resumen.length() - 2);
        resumen.append(". Muestras de EQUIPO (hasta 10): ");
        agregarMuestras(resumen, diagnosticos, Tipo.EQUIPO, 10);
        resumen.append(". Muestras de JUGADOR (hasta 20): ");
        agregarMuestras(resumen, diagnosticos, Tipo.JUGADOR, 20);
        return resumen.toString();
    }

    private static void agregarMuestras(StringBuilder resumen, List<Diagnostico> diagnosticos,
            Tipo tipo, int limite) {
        var muestras = diagnosticos.stream().filter(d -> d.tipo() == tipo).limit(limite).toList();
        if (muestras.isEmpty()) {
            resumen.append("ninguna");
            return;
        }
        for (int i = 0; i < muestras.size(); i++) {
            if (i > 0) resumen.append(" | ");
            resumen.append("[").append(formatear(muestras.get(i))).append("]");
        }
        long restantes = diagnosticos.stream().filter(d -> d.tipo() == tipo).count() - muestras.size();
        if (restantes > 0) resumen.append(" (+").append(restantes).append(" más)");
    }

    private static String formatear(Diagnostico diagnostico) {
        return "proveedor=" + valor(diagnostico.proveedor())
                + "; externalId=" + valor(diagnostico.externalId())
                + "; nombre=" + valor(diagnostico.nombre())
                + "; competicion=" + valor(diagnostico.competicion())
                + (diagnostico.equipoWhoScored() == null ? "" : "; equipoWhoScored=" + valor(diagnostico.equipoWhoScored()))
                + (diagnostico.equipoLocal() == null ? "" : "; equipoLocal=" + valor(diagnostico.equipoLocal()))
                + "; mejorCandidato=" + candidato(diagnostico.mejorCandidato())
                + "; segundoCandidato=" + candidato(diagnostico.segundoCandidato())
                + "; motivo=" + diagnostico.motivo();
    }

    private static String candidato(Candidato candidato) {
        return candidato == null ? "ninguno" : valor(candidato.nombre()) + " (id="
                + valor(candidato.id()) + ", score=" + valor(candidato.score()) + ")";
    }

    private static String valor(Object valor) {
        return valor == null ? "-" : String.valueOf(valor);
    }
}
