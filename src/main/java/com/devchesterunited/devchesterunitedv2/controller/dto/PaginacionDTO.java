package com.devchesterunited.devchesterunitedv2.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PaginacionDTO(
        @JsonProperty("current_page") int paginaActual,
        @JsonProperty("page_size") int tamanoPagina,
        @JsonProperty("total_pages") long totalPaginas,
        @JsonProperty("total_records") long totalRegistros) { }
