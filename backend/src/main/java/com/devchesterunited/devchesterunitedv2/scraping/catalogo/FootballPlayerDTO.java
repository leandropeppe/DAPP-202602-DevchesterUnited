package com.devchesterunited.devchesterunitedv2.scraping.catalogo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballPlayerDTO(Long id, String name,
        Integer shirtNumber, String nationality, String position) { }
