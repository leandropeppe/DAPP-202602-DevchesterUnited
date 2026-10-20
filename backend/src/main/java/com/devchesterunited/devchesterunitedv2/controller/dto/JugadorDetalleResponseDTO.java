package com.devchesterunited.devchesterunitedv2.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record JugadorDetalleResponseDTO(
        String id,
        @JsonProperty("name") String nombre,
        @JsonProperty("league") String liga,
        @JsonProperty("team") String equipo,
        @JsonProperty("position") String posicion,
        @JsonProperty("jersey_number") int dorsal,
        @JsonProperty("nationality") String nacionalidad,
        @JsonProperty("statistics") EstadisticasJugadorResponseDTO estadisticas,
        BigDecimal valuation) { }
