package com.devchesterunited.devchesterunitedv2.scraping.catalogo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballTeamsResponseDTO(Integer count, FootballCompetitionDTO competition,
        FootballSeasonDTO season, List<FootballTeamDTO> teams) { }
