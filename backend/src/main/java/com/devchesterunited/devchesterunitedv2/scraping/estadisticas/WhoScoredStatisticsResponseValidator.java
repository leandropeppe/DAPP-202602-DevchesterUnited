package com.devchesterunited.devchesterunitedv2.scraping.estadisticas;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.CompeticionEstadistica;

import java.util.ArrayList;
import java.util.List;

/** Valida la integridad y completitud de respuestas de statisticsfeed. */
public final class WhoScoredStatisticsResponseValidator {
    public void validar(WhoScoredStatisticsFeedResponseDTO response,
            CompeticionEstadistica competicion, String familia, String url) {
        List<String> failures = new ArrayList<>();
        if (response == null) {
            failures.add("response == null");
            throw errorDeCompletitud(new CompletitudContext(competicion, familia,
                    Metadata.vacia(), url, failures));
        }
        Metadata metadata = new Metadata(response.currentPage(), response.totalPages(),
                response.resultsPerPage(), response.totalResults(),
                response.players() == null ? null : response.players().size());
        agregarFallosMetadata(metadata, failures);
        if (!failures.isEmpty()) {
            throw errorDeCompletitud(new CompletitudContext(competicion, familia, metadata,
                    url, failures));
        }
    }

    private void agregarFallosMetadata(Metadata metadata, List<String> failures) {
        agregarFallosNulos(metadata, failures);
        agregarFallosRangos(metadata, failures);
        agregarFallosCantidades(metadata, failures);
    }

    private void agregarFallosNulos(Metadata metadata, List<String> failures) {
        agregarFalloSiNulo(metadata.currentPage(), "currentPage == null", failures);
        agregarFalloSiNulo(metadata.totalPages(), "totalPages == null", failures);
        agregarFalloSiNulo(metadata.totalResults(), "totalResults == null", failures);
        agregarFalloSiNulo(metadata.resultsPerPage(), "resultsPerPage == null", failures);
        agregarFalloSiNulo(metadata.playersReceived(), "playerTableStats == null", failures);
    }

    private void agregarFallosRangos(Metadata metadata, List<String> failures) {
        agregarFalloSiMenorQue(metadata.currentPage(), 1, "currentPage < 1", failures);
        agregarFalloSiMenorQue(metadata.totalPages(), 1, "totalPages < 1", failures);
        agregarFalloSiMenorQue(metadata.totalResults(), 0, "totalResults < 0", failures);
        agregarFalloSiMenorQue(metadata.resultsPerPage(), 1, "resultsPerPage < 1", failures);
        agregarFalloSiDistintoDe(metadata.currentPage(), 1, "currentPage != 1", failures);
        agregarFalloSiDistintoDe(metadata.totalPages(), 1, "totalPages != 1", failures);
    }

    private void agregarFallosCantidades(Metadata metadata, List<String> failures) {
        if (hayCantidadIncompatible(metadata)) {
            failures.add("totalResults > resultsPerPage");
        }
        if (hayCantidadDeJugadoresIncompatible(metadata)) {
            failures.add("playersRecibidos != totalResults");
        }
    }

    private void agregarFalloSiNulo(Integer value, String failure, List<String> failures) {
        if (value == null) {
            failures.add(failure);
        }
    }

    private void agregarFalloSiMenorQue(Integer value, int limit, String failure,
            List<String> failures) {
        if (value != null && value < limit) {
            failures.add(failure);
        }
    }

    private void agregarFalloSiDistintoDe(Integer value, int expected, String failure,
            List<String> failures) {
        if (value != null && value != expected) {
            failures.add(failure);
        }
    }

    private boolean hayCantidadIncompatible(Metadata metadata) {
        return metadata.totalResults() != null && metadata.resultsPerPage() != null
                && metadata.totalResults() > metadata.resultsPerPage();
    }

    private boolean hayCantidadDeJugadoresIncompatible(Metadata metadata) {
        return metadata.playersReceived() != null && metadata.totalResults() != null
                && !metadata.playersReceived().equals(metadata.totalResults());
    }

    private WhoScoredStatisticsException errorDeCompletitud(CompletitudContext context) {
        return new WhoScoredStatisticsException("WhoScored devolvio una respuesta paginada o incompleta: "
                + "competicion=" + context.competicion() + ", familia=" + context.familia()
                + ", currentPage=" + context.metadata().currentPage() + ", totalPages="
                + context.metadata().totalPages() + ", resultsPerPage="
                + context.metadata().resultsPerPage() + ", totalResults="
                + context.metadata().totalResults() + ", playersRecibidos="
                + context.metadata().playersReceived() + ", adquisicion="
                + sanitizarAdquisicion(context.url()) + ", fallos=" + context.failures());
    }

    private String sanitizarAdquisicion(String url) {
        return url == null ? null : url.replaceAll(
                "(?i)(api[-_]?key|token|authorization|password|secret)=[^&]*", "$1=[REDACTED]");
    }

    private record Metadata(Integer currentPage, Integer totalPages, Integer resultsPerPage,
            Integer totalResults, Integer playersReceived) {
        private static Metadata vacia() {
            return new Metadata(null, null, null, null, null);
        }
    }

    private record CompletitudContext(CompeticionEstadistica competicion, String familia,
            Metadata metadata, String url, List<String> failures) { }
}
