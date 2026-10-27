package com.devchesterunited.devchesterunitedv2.scraping.catalogo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** DTO mínimo para resolver duplicados de jugadores entre equipos. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballTeamDetailsDTO(Long id, String lastUpdated) { }
