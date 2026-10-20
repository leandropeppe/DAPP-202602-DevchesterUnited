package com.devchesterunited.devchesterunitedv2.controller.dto;

import java.util.List;

public record ValuacionHistoryResponseDTO(List<ValuacionResponseDTO> items, int page, int limit, long total) {
    public ValuacionHistoryResponseDTO {
        items = List.copyOf(items);
    }
}
