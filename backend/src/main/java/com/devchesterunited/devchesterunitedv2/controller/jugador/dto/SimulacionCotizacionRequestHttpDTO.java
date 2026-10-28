package com.devchesterunited.devchesterunitedv2.controller.jugador.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = false)
public record SimulacionCotizacionRequestHttpDTO(
        @NotNull(message = "horizonMatches es obligatorio.") Integer horizonMatches,
        @NotNull(message = "modifications es obligatorio.") @Valid Map<String, BigDecimal> modifications) {
    @JsonIgnore
    @jakarta.validation.constraints.AssertTrue(message = "horizonMatches debe ser 3, 5, 8 o 10.")
    public boolean horizontePermitido() {
        return horizonMatches != null && Set.of(3, 5, 8, 10).contains(horizonMatches);
    }
}
