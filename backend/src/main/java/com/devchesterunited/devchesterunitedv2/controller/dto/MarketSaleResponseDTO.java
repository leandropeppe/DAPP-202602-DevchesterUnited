package com.devchesterunited.devchesterunitedv2.controller.dto;

import com.devchesterunited.devchesterunitedv2.service.dto.MarketSaleResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Vista publica del resultado de una solicitud de venta. */
public record MarketSaleResponseDTO(
        @JsonProperty("operation_id")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        UUID operacionId,
        @JsonProperty("transaction_id")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        UUID transaccionId,
        @JsonProperty("player_id")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        UUID jugadorId,
        @JsonProperty("quantity")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Long cantidad,
        @JsonProperty("unit_price")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        BigDecimal precioUnitario,
        @JsonProperty("total_amount")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        BigDecimal montoTotal,
        @JsonProperty("status")
        String estado,
        @JsonProperty("updated_at")
        Instant actualizadoEn) {

    public static MarketSaleResponseDTO fromDomain(MarketSaleResult result) {
        return new MarketSaleResponseDTO(
                result.solicitudId(),
                result.transaccionId(),
                result.jugadorId(),
                result.cantidad(),
                result.precioUnitario(),
                result.montoTotal(),
                result.estado().name(),
                result.actualizadoEn());
    }
}
