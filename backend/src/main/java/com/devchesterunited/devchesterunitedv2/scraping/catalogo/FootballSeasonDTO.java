package com.devchesterunited.devchesterunitedv2.scraping.catalogo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FootballSeasonDTO(Long id, LocalDate startDate, LocalDate endDate) { }
