package com.devchesterunited.devchesterunitedv2.adapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballCompetitionDTO(String code) { }
