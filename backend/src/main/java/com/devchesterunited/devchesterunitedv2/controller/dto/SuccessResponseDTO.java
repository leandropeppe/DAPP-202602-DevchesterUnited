package com.devchesterunited.devchesterunitedv2.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public record SuccessResponseDTO<T>(@JsonInclude(JsonInclude.Include.NON_NULL) String message, T data) {
    public SuccessResponseDTO(T data) {
        this(null, data);
    }

    @JsonProperty("status")
    public String status() {
        return "success";
    }
}
