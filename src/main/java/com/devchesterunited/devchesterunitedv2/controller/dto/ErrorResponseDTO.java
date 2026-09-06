package com.devchesterunited.devchesterunitedv2.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ErrorResponseDTO(String message) {
    @JsonProperty("status")
    public String status() {
        return "error";
    }
}
