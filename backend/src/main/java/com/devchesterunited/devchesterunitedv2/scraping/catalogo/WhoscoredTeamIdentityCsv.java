package com.devchesterunited.devchesterunitedv2.scraping.catalogo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Parser estricto del CSV de enrollment aprobado. No accede a red ni a la base. */
public final class WhoscoredTeamIdentityCsv {
    public static final String HEADER = "whoscored_team_id,football_data_external_id";

    private WhoscoredTeamIdentityCsv() { }

    public static List<Mapping> read(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !HEADER.equals(stripBom(lines.get(0)))) {
            throw new IllegalArgumentException("El CSV debe tener exactamente el header: " + HEADER);
        }
        List<Mapping> mappings = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;
            String[] values = line.split(",", -1);
            if (values.length != 2) {
                throw new IllegalArgumentException("Se esperaban dos columnas en la línea " + (i + 1));
            }
            mappings.add(new Mapping(parse(values[0], i + 1), parse(values[1], i + 1)));
        }
        return List.copyOf(mappings);
    }

    private static String stripBom(String value) {
        return value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    private static long parse(String value, int line) {
        if (value.isBlank()) throw new IllegalArgumentException("ID vacío en la línea " + line);
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("ID inválido en la línea " + line, exception);
        }
    }

    public record Mapping(long whoscoredTeamId, long footballDataExternalId) { }
}
