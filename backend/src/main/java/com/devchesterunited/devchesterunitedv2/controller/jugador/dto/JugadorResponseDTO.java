package com.devchesterunited.devchesterunitedv2.controller.jugador.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/** Perfil público; el Service suministra el identificador opaco ply_ más UUID. */
public record JugadorResponseDTO(
        String id,
        @JsonProperty("name") String nombre,
        @JsonProperty("league") String liga,
        @JsonProperty("team") String equipo,
        @JsonProperty("position") String posicion,
        @JsonProperty("jersey_number") int dorsal,
        @JsonProperty("nationality") String nacionalidad,
        BigDecimal valuation) { }
