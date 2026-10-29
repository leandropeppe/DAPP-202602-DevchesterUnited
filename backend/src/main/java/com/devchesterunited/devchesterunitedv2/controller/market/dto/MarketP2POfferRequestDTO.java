package com.devchesterunited.devchesterunitedv2.controller.market.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2POfferCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Request de publicación; no contiene precio ni identidad seleccionable. */
public final class MarketP2POfferRequestDTO {
    @NotNull
    @JsonProperty("player_id")
    private UUID jugadorId;

    @NotNull
    @Positive
    @JsonProperty("quantity")
    private Long cantidad;

    private final Set<String> camposDesconocidos = new HashSet<>();

    @JsonAnySetter
    public void registrarCampoDesconocido(String nombre, JsonNode valor) {
        camposDesconocidos.add(nombre);
    }

    public void validarCamposAdicionales() {
        if (camposDesconocidos.stream().anyMatch(this::esPrecio)) {
            throw new TokenMarketException(TokenMarketException.Reason.MANUAL_PRICE_NOT_ALLOWED,
                    "El precio manual no esta permitido.");
        }
        if (!camposDesconocidos.isEmpty()) {
            throw new TokenMarketException(TokenMarketException.Reason.INVALID_INPUT,
                    "La solicitud contiene campos no permitidos.");
        }
    }

    public MarketP2POfferCommand toCommand(UUID usuarioId, String claveIdempotencia) {
        return new MarketP2POfferCommand(usuarioId, jugadorId, cantidad, claveIdempotencia);
    }

    private boolean esPrecio(String nombre) {
        return "price".equals(nombre) || "unit_price".equals(nombre)
                || "unitPrice".equals(nombre) || "manual_price".equals(nombre)
                || "manualPrice".equals(nombre);
    }
}
