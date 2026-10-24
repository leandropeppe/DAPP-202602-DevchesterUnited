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
        boolean resultadoCombinadoCompleto,
        @JsonProperty("open_players")
        long jugadoresAbiertos,
        @JsonProperty("total_tokens")
        long tokensTotales,
        @JsonProperty("total_pending_acquisition_cost")
        BigDecimal costoPendienteTotal,
        @JsonProperty("total_current_value")
        BigDecimal valorActualTotal) {

    public MarketPortfolioResponseDTO {
        posicionesAbiertas = List.copyOf(Objects.requireNonNull(posicionesAbiertas));
        Objects.requireNonNull(pnlRealizado);
        Objects.requireNonNull(costoPendienteTotal);
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
                result.resultadoCombinadoCompleto(),
                result.jugadoresAbiertos(),
                result.tokensTotales(),
                result.costoPendienteTotal(),
                result.valorActualTotal().orElse(null));
    }
}
