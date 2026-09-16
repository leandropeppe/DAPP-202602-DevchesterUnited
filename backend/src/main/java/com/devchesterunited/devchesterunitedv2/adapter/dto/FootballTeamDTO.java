package com.devchesterunited.devchesterunitedv2.adapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballTeamDTO(Long id, String name, String shortName, String tla, String crest,
        List<FootballPlayerDTO> squad) { }
