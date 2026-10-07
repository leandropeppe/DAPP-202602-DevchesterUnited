package com.devchesterunited.devchesterunitedv2.adapter.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.devchesterunited.devchesterunitedv2.adapter.ProveedorEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.adapter.WhoScoredStatisticsException;
import com.devchesterunited.devchesterunitedv2.adapter.dto.WhoScoredStatisticsResponseDTO;
import com.devchesterunited.devchesterunitedv2.modelo.CompeticionEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.FamiliaEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.IdentidadEstadisticaJugador;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.PaginaEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.RegistroEstadisticasJugador;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
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
    private final ObjectMapper objectMapper;

    @Autowired
    public WhoScoredStatisticsAdapter(RestClient.Builder builder,
            org.springframework.core.env.Environment environment) {
        this(builder, environment.getProperty("whoscored.base-url", ""),
                environment.getProperty("whoscored.api-key", ""),
                environment.getProperty("whoscored.max-response-bytes", Integer.class, 10_485_760),
                new ObjectMapper());
    }

    public WhoScoredStatisticsAdapter(RestClient.Builder builder, String baseUrl,
            String apiKey, int maxResponseBytes) {
        this(builder, baseUrl, apiKey, maxResponseBytes, new ObjectMapper());
    }

    WhoScoredStatisticsAdapter(RestClient.Builder builder, String baseUrl,
            String apiKey, int maxResponseBytes, ObjectMapper objectMapper) {
        this.baseUrl = Objects.requireNonNull(baseUrl).replaceAll("/+$", "");
        this.client = this.baseUrl.isBlank() ? builder.build() : builder.baseUrl(this.baseUrl).build();
        this.apiKey = apiKey == null ? "" : apiKey;
        if (maxResponseBytes < 1) {
            throw new IllegalArgumentException("El límite de respuesta debe ser positivo.");
        }
        this.maxResponseBytes = maxResponseBytes;
        this.objectMapper = Objects.requireNonNull(objectMapper);
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
                        if (!apiKey.isBlank()) {
                            headers.setBearerAuth(apiKey);
                        }
                    })
                    .exchange((request, responseResult) -> {
                        if (responseResult.getStatusCode().value() == 429) {
                            throw new WhoScoredStatisticsException("El proveedor estadístico alcanzó su cuota.");
                        }
                        if (responseResult.getStatusCode().isError()) {
                            throw new WhoScoredStatisticsException("WhoScored devolvió una respuesta no exitosa.");
                        }
                        try {
                            return objectMapper.readValue(leerLimitado(responseResult.getBody()),
                                    WhoScoredStatisticsResponseDTO.class);
                        } catch (IOException exception) {
                            throw new WhoScoredStatisticsException("La respuesta de WhoScored no es un JSON válido.", exception);
                        }
                    });
            return traducir(response, competicion, familia, pagina, tamano);
        } catch (WhoScoredStatisticsException exception) {
            throw exception;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new WhoScoredStatisticsException("No se pudo obtener una página válida de WhoScored.", exception);
        }
    }

    private byte[] leerLimitado(InputStream body) throws IOException {
        byte[] buffer = new byte[8192];
        int total = 0;
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        int leidos;
        while ((leidos = body.read(buffer)) != -1) {
            total += leidos;
            if (total > maxResponseBytes) {
                throw new WhoScoredStatisticsException("La respuesta de WhoScored supera el límite configurado.");
            }
            result.write(buffer, 0, leidos);
        }
        return result.toByteArray();
    }

    private PaginaEstadisticasJugador traducir(WhoScoredStatisticsResponseDTO response,
            CompeticionEstadistica competicion, FamiliaEstadistica familia,
            int requestedPage, int requestedSize) {
        if (response == null || response.page() == null || response.pageSize() == null
                || response.totalPages() == null || response.totalResults() == null
                || response.players() == null || response.page() != requestedPage
                || response.pageSize() != requestedSize || response.totalPages() < requestedPage
                || response.totalPages() < 1 || response.totalResults() < 0
                || response.players().size() > requestedSize
                || response.totalResults() < response.players().size()) {
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
        boolean teamIdAbsent = player.teamId() == null;
        boolean teamNameAbsent = player.teamName() == null || player.teamName().isBlank();
        if (teamIdAbsent != teamNameAbsent) {
            throw new WhoScoredStatisticsException("WhoScored proporcionó hints de equipo inconsistentes.");
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
