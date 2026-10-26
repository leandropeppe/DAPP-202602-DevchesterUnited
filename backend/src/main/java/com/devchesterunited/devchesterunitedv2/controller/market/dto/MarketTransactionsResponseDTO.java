package com.devchesterunited.devchesterunitedv2.controller.market.dto;

import com.devchesterunited.devchesterunitedv2.controller.dto.PaginacionDTO;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketTransactionResult;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/** Respuesta paginada del historial confirmado del usuario autenticado. */
public record MarketTransactionsResponseDTO(
        @JsonProperty("transactions") List<MarketTransactionResponseDTO> transacciones,
        @JsonProperty("pagination") PaginacionDTO paginacion) {

    public MarketTransactionsResponseDTO {
        transacciones = List.copyOf(Objects.requireNonNull(transacciones));
        Objects.requireNonNull(paginacion);
    }

    public static MarketTransactionsResponseDTO fromDomain(MarketTransactionResult result) {
        Objects.requireNonNull(result, "El resultado del historial es obligatorio.");
        var pagination = result.paginacion();
        return new MarketTransactionsResponseDTO(
                result.transacciones().stream()
                        .map(MarketTransactionResponseDTO::fromDomain)
                        .toList(),
                new PaginacionDTO(
                        pagination.paginaActual(),
                        pagination.tamanoPagina(),
                        pagination.totalPaginas(),
                        pagination.totalRegistros()));
    }
}
