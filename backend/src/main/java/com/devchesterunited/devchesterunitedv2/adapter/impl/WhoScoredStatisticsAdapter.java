package com.devchesterunited.devchesterunitedv2.adapter.impl;

import com.devchesterunited.devchesterunitedv2.adapter.ProveedorEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.adapter.WhoScoredStatisticsException;
import com.devchesterunited.devchesterunitedv2.adapter.dto.WhoScoredStatisticsResponseDTO;
import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.FamiliaEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.IdentidadEstadisticaJugador;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.PaginaEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.RegistroEstadisticasJugador;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatusCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Adapter de WhoScored. El formato HTTP/JSON queda completamente confinado aquí. */
@Component
public class WhoScoredStatisticsAdapter implements ProveedorEstadisticasJugador {
    private final RestClient client;
    private final String baseUrl;
    private final String apiKey;
    private final int maxResponseBytes;

    @Autowired
    public WhoScoredStatisticsAdapter(RestClient.Builder builder,
            org.springframework.core.env.Environment environment) {
        this(builder, environment.getProperty("whoscored.base-url", ""),
                environment.getProperty("whoscored.api-key", ""),
                environment.getProperty("whoscored.max-response-bytes", Integer.class, 10_485_760));
    }

    public WhoScoredStatisticsAdapter(RestClient.Builder builder, String baseUrl,
            String apiKey, int maxResponseBytes) {
        this.baseUrl = Objects.requireNonNull(baseUrl).replaceAll("/+$", "");
        this.client = this.baseUrl.isBlank() ? builder.build() : builder.baseUrl(this.baseUrl).build();
        this.apiKey = apiKey == null ? "" : apiKey;
        this.maxResponseBytes = maxResponseBytes;
    }

    @Override
    public PaginaEstadisticasJugador obtenerPagina(CompeticionEstadistica competicion,
            FamiliaEstadistica familia, int pagina, int tamano) {
        if (pagina < 1 || tamano < 1) {
            throw new IllegalArgumentException("La página y el tamaño deben ser positivos.");
        }
        if (baseUrl.isBlank()) {
            throw new WhoScoredStatisticsException("WhoScored no está configurado.");
        }
        try {
            WhoScoredStatisticsResponseDTO response = client.get()
                    .uri(uriBuilder -> uriBuilder.path("/statistics")
                            .queryParam("competition", competicion.name())
                            .queryParam("family", familia.name())
                            .queryParam("page", pagina)
                            .queryParam("pageSize", tamano).build())
                    .headers(headers -> {
                        if (!apiKey.isBlank()) headers.setBearerAuth(apiKey);
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, responseResult) -> {
                        throw new WhoScoredStatisticsException("WhoScored devolvió una respuesta no exitosa.");
                    })
                    .body(WhoScoredStatisticsResponseDTO.class);
            return traducir(response, competicion, familia, pagina, tamano);
        } catch (WhoScoredStatisticsException exception) {
            throw exception;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new WhoScoredStatisticsException("No se pudo obtener una página válida de WhoScored.", exception);
        }
    }

    private PaginaEstadisticasJugador traducir(WhoScoredStatisticsResponseDTO response,
            CompeticionEstadistica competicion, FamiliaEstadistica familia,
            int requestedPage, int requestedSize) {
        if (response == null || response.page() == null || response.pageSize() == null
                || response.totalPages() == null || response.totalResults() == null
                || response.players() == null || response.page() != requestedPage
                || response.pageSize() != requestedSize || response.totalPages() < requestedPage
                || response.totalPages() < 1 || response.totalResults() < 0
                || response.players().size() > requestedSize) {
            throw new WhoScoredStatisticsException("La metadata de paginación de WhoScored es inválida.");
        }
        List<RegistroEstadisticasJugador> registros = response.players().stream()
                .map(player -> traducirJugador(player, familia)).toList();
        return new PaginaEstadisticasJugador(competicion, familia, response.page(),
                response.pageSize(), response.totalPages(), response.totalResults(), registros);
    }

    private RegistroEstadisticasJugador traducirJugador(WhoScoredStatisticsResponseDTO.PlayerDTO player,
            FamiliaEstadistica familia) {
        if (player == null || player.playerId() == null || player.playerId() <= 0
                || player.name() == null || player.name().isBlank()) {
            throw new WhoScoredStatisticsException("WhoScored no proporcionó identidad suficiente del jugador.");
        }
        var identidad = new IdentidadEstadisticaJugador(player.playerId(), normalizar(player.name()),
                normalizarOpcional(player.position()), normalizarOpcional(player.nationality()),
                player.teamId(), normalizarOpcional(player.teamName()));
        var metricas = new MetricasEstadisticasJugador(player.minutesPlayed(), player.goals(), player.assists(),
                player.shots(), player.keyPasses(), player.dribbles(), player.tackles(), player.rating(),
                player.yellowCards(), player.redCards());
        return new RegistroEstadisticasJugador(identidad, familia, metricas);
    }

    private static String normalizar(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private static String normalizarOpcional(String value) {
        return value == null || value.isBlank() ? null : normalizar(value);
    }
}
