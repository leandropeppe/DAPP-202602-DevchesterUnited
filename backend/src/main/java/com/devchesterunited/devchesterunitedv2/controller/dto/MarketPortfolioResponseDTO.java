package com.devchesterunited.devchesterunitedv2.controller.dto;

import com.devchesterunited.devchesterunitedv2.service.dto.MarketPortfolioResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Vista publica del portfolio del usuario autenticado. */
public record MarketPortfolioResponseDTO(
        @JsonProperty("open_positions")
        List<MarketPortfolioPositionResponseDTO> posicionesAbiertas,
        @JsonProperty("realized_pnl")
        BigDecimal pnlRealizado,
        @JsonProperty("unrealized_pnl")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        BigDecimal pnlNoRealizado,
        @JsonProperty("combined_result")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        BigDecimal resultadoCombinado,
        @JsonProperty("combined_result_complete")
        boolean resultadoCombinadoCompleto) {

    public MarketPortfolioResponseDTO {
        posicionesAbiertas = List.copyOf(Objects.requireNonNull(posicionesAbiertas));
        Objects.requireNonNull(pnlRealizado);
    }

    public static MarketPortfolioResponseDTO fromDomain(MarketPortfolioResult result) {
        Objects.requireNonNull(result, "El resultado del portfolio es obligatorio.");
        return new MarketPortfolioResponseDTO(
                result.posicionesAbiertas().stream()
                        .map(MarketPortfolioPositionResponseDTO::fromDomain)
                        .toList(),
                result.pnlRealizado(),
                result.pnlNoRealizado().orElse(null),
                result.resultadoCombinado().orElse(null),
                result.resultadoCombinadoCompleto());
    }
}
