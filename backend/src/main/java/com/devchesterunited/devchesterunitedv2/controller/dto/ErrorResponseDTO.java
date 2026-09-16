package com.devchesterunited.devchesterunitedv2.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record ErrorResponseDTO(String message, @JsonInclude(JsonInclude.Include.NON_NULL) List<DetalleErrorDTO> errors) {
    public ErrorResponseDTO(String message) { this(message, null); }

    @JsonProperty("status")
    public String status() {
        return "error";
    }

    public record DetalleErrorDTO(String field, String issue) { }
}
