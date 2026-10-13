package com.devchesterunited.devchesterunitedv2.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record EstadisticasJugadorResponseDTO(
        @JsonProperty("position") String posicion,
        @JsonProperty("minutes_played") Integer minutosJugados,
        @JsonProperty("goals") Integer goles,
        @JsonProperty("assists") Integer asistencias,
        @JsonProperty("shots") BigDecimal tiros,
        @JsonProperty("key_passes") BigDecimal pasesClave,
        @JsonProperty("dribbles") BigDecimal regates,
        @JsonProperty("tackles") BigDecimal tackles,
        @JsonProperty("rating") BigDecimal valoracion,
        @JsonProperty("yellow_cards") Integer tarjetasAmarillas,
        @JsonProperty("red_cards") Integer tarjetasRojas) { }
