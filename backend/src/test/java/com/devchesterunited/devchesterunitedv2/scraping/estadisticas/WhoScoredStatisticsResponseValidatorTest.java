package com.devchesterunited.devchesterunitedv2.scraping.estadisticas;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.CompeticionEstadistica;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhoScoredStatisticsResponseValidatorTest {
    private final WhoScoredStatisticsResponseValidator validator =
            new WhoScoredStatisticsResponseValidator();

    @Test
    void aceptaRespuestaCompletaConListaVacia() {
        assertThatCode(() -> validator.validar(respuesta(1, 1, 500, 0, List.of()),
                CompeticionEstadistica.PREMIER_LEAGUE, "SUMMARY", url()))
                .doesNotThrowAnyException();
    }

    @Test
    void rechazaRespuestaNulaConContextoCompleto() {
        assertThatThrownBy(() -> validator.validar(null, CompeticionEstadistica.PREMIER_LEAGUE,
                "SUMMARY", url()))
                .isInstanceOf(WhoScoredStatisticsException.class)
                .hasMessageContaining("response == null")
                .hasMessageContaining("competicion=PREMIER_LEAGUE")
                .hasMessageContaining("familia=SUMMARY");
    }

    @Test
    void rechazaMetadataAusenteYListaNula() {
        var response = new WhoScoredStatisticsFeedResponseDTO(null, null);

        assertThatThrownBy(() -> validator.validar(response, CompeticionEstadistica.PREMIER_LEAGUE,
                "SUMMARY", url()))
                .isInstanceOf(WhoScoredStatisticsException.class)
                .hasMessageContaining("fallos=[currentPage == null, totalPages == null, "
                        + "totalResults == null, resultsPerPage == null, playerTableStats == null]");
    }

    @Test
    void rechazaPaginacionYResultadosIncompatibles() {
        var response = respuesta(1, 2, 500, 2, Collections.singletonList(player()));

        assertThatThrownBy(() -> validator.validar(response, CompeticionEstadistica.PREMIER_LEAGUE,
                "SUMMARY", url()))
                .isInstanceOf(WhoScoredStatisticsException.class)
                .hasMessageContaining("totalPages != 1")
                .hasMessageContaining("playersRecibidos != totalResults");
    }

    @Test
    void rechazaTotalResultsMayorQueLaPagina() {
        var response = respuesta(1, 1, 1, 2, List.of(player(), player()));

        assertThatThrownBy(() -> validator.validar(response, CompeticionEstadistica.PREMIER_LEAGUE,
                "SUMMARY", url()))
                .isInstanceOf(WhoScoredStatisticsException.class)
                .hasMessageContaining("totalResults > resultsPerPage");
    }

    @Test
    void conservaElOrdenExactoDeFallosDeRangosPaginacionYCantidades() {
        var response = respuesta(0, 0, 0, 2, List.of(player()));

        var exception = assertThatThrownBy(() -> validator.validar(response,
                CompeticionEstadistica.PREMIER_LEAGUE, "SUMMARY", url()))
                .isInstanceOf(WhoScoredStatisticsException.class)
                .actual();

        assertThat(exception.getMessage()).contains(
                "fallos=[currentPage < 1, totalPages < 1, resultsPerPage < 1, "
                        + "currentPage != 1, totalPages != 1, totalResults > resultsPerPage, "
                        + "playersRecibidos != totalResults]");
    }

    @Test
    void rechazaValoresLimiteSegunLasReglasExistentes() {
        var response = respuesta(0, 0, 0, -1, List.of());

        var exception = assertThatThrownBy(() -> validator.validar(response,
                CompeticionEstadistica.PREMIER_LEAGUE, "SUMMARY", url()))
                .isInstanceOf(WhoScoredStatisticsException.class)
                .actual();

        assertThat(exception.getMessage()).contains(
                "fallos=[currentPage < 1, totalPages < 1, totalResults < 0, "
                        + "resultsPerPage < 1, currentPage != 1, totalPages != 1, "
                        + "playersRecibidos != totalResults]");
    }

    @Test
    void conservaElMensajeCompletoYElContextoDeCompeticionYFamilia() {
        var response = respuesta(1, 2, 500, 2, List.of(player()));

        var exception = assertThatThrownBy(() -> validator.validar(response,
                CompeticionEstadistica.PREMIER_LEAGUE, "DEFENSIVE", url()))
                .isInstanceOf(WhoScoredStatisticsException.class)
                .actual();

        assertThat(exception.getMessage()).isEqualTo(
                "WhoScored devolvio una respuesta paginada o incompleta: "
                        + "competicion=PREMIER_LEAGUE, familia=DEFENSIVE, currentPage=1, "
                        + "totalPages=2, resultsPerPage=500, totalResults=2, playersRecibidos=1, "
                        + "adquisicion=https://example.test/feed?stageId=1, "
                        + "fallos=[totalPages != 1, playersRecibidos != totalResults]");
    }

    @Test
    void sanitizaParametrosSensiblesEnElMensaje() {
        var response = respuesta(1, 1, 500, 2, List.of(player()));
        String sensitiveUrl = "https://example.test/feed?token=secret&api_key=private&stageId=1";

        var exception = assertThatThrownBy(() -> validator.validar(response,
                CompeticionEstadistica.PREMIER_LEAGUE, "SUMMARY", sensitiveUrl))
                .isInstanceOf(WhoScoredStatisticsException.class)
                .hasMessageContaining("playersRecibidos != totalResults")
                .actual();

        assertThat(exception.getMessage())
                .contains("token=[REDACTED]")
                .contains("api_key=[REDACTED]")
                .doesNotContain("secret")
                .doesNotContain("private");
    }

    private WhoScoredStatisticsFeedResponseDTO respuesta(Integer currentPage, Integer totalPages,
            Integer resultsPerPage, Integer totalResults,
            List<WhoScoredStatisticsFeedResponseDTO.PlayerDTO> players) {
        var paging = new WhoScoredStatisticsFeedResponseDTO.PagingDTO(currentPage, totalPages,
                resultsPerPage, totalResults);
        return new WhoScoredStatisticsFeedResponseDTO(paging, players);
    }

    private WhoScoredStatisticsFeedResponseDTO.PlayerDTO player() {
        return new WhoScoredStatisticsFeedResponseDTO.PlayerDTO(101L, "Ana Perez", 201L,
                "Equipo", null, null, null, null, null, null, null, null, null, null);
    }

    private String url() {
        return "https://example.test/feed?stageId=1";
    }
}
