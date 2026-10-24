package com.devchesterunited.devchesterunitedv2.service.dto.valorizacion;

import java.util.List;

public record ValuacionHistoricoDTO(List<ValuacionDTO> items, int page, int limit, long total) {
    public ValuacionHistoricoDTO {
        items = List.copyOf(items);
    }
}
