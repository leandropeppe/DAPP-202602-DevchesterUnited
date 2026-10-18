package com.devchesterunited.devchesterunitedv2.controller.dto;

import com.devchesterunited.devchesterunitedv2.service.dto.MarketSaleRequest;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/**
 * Cuerpo HTTP de una venta. La identidad y la Idempotency-Key llegan por
 * autenticacion/cabecera y se agregan al convertir al comando interno.
 */
public record MarketSaleRequestDTO(
        @JsonProperty("player_id")
        @NotNull(message = "El jugador es obligatorio.")
        UUID jugadorId,
        @JsonProperty("quantity")
        @NotNull(message = "La cantidad es obligatoria.")
        @Positive(message = "La cantidad debe ser un entero positivo.")
        @Max(value = 100, message = "La cantidad no puede superar la emision maxima del jugador.")
        Long cantidad,
        @JsonProperty("quote_id")
        @NotNull(message = "La cotizacion es obligatoria.")
        UUID cotizacionId,
        @JsonProperty("quote_version")
        @NotNull(message = "La version de cotizacion es obligatoria.")
        @Positive(message = "La version de cotizacion debe ser positiva.")
        Long versionCotizacion) {

    /** Agrega exclusivamente la identidad autenticada y la clave de cabecera. */
    public MarketSaleRequest toDomain(UUID usuarioId, String claveIdempotencia) {
        return new MarketSaleRequest(usuarioId, claveIdempotencia, jugadorId,
                cantidad, cotizacionId, versionCotizacion);
    }
}
