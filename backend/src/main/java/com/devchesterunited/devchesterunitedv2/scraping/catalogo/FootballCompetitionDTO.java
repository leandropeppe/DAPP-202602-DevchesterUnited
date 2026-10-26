package com.devchesterunited.devchesterunitedv2.scraping.catalogo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballCompetitionDTO(String code) { }
