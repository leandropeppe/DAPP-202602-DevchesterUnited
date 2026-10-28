package com.devchesterunited.devchesterunitedv2.modelo.simulacion;

import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaValorizacion;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Resuelve posiciones de entrada sin duplicar las reglas de normalización de las estrategias. */
public final class ResolutorPosicionSimulacion {
    public Resolucion resolver(String posicionOriginal, EstrategiaValorizacion estrategia) {
        List<String> originales = separar(posicionOriginal);
        List<String> compatibles = originales.stream()
                .map(ResolutorPosicionSimulacion::canonica)
                .distinct()
                .filter(posicion -> estrategia != null && estrategia.soporta(posicion))
                .toList();
        if (compatibles.size() == 1) {
            return new Resolucion(originales, compatibles.get(0), null);
        }
        if (compatibles.size() > 1 || originales.size() > 1) {
            return new Resolucion(originales, null, "POSICION_AMBIGUA");
        }
        return new Resolucion(originales, null, "POSICION_NO_SOPORTADA");
    }

    private static List<String> separar(String original) {
        if (original == null || original.isBlank()) return List.of();
        return Arrays.stream(original.split("[,/|;]"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private static String canonica(String posicion) {
        if (posicion == null) return null;
        return switch (posicion.trim().toUpperCase(Locale.ROOT)) {
            case "FW", "FWD", "FORWARD", "DL" -> "Forward";
            case "MF", "MID", "MIDFIELDER", "MC" -> "Midfielder";
            case "DF", "DEF", "DEFENDER" -> "Defender";
            case "GK", "KEEPER", "GOALKEEPER", "PO" -> "Goalkeeper";
            default -> posicion.trim();
        };
    }

    public record Resolucion(List<String> originales, String aplicada, String blockingReason) {
        public Resolucion {
            originales = List.copyOf(originales == null ? List.of() : originales);
        }

        public boolean resoluble() {
            return aplicada != null && blockingReason == null;
        }
    }
}
