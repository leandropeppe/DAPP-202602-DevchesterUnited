package com.devchesterunited.devchesterunitedv2.service.dto.simulacion;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public record SimulacionCotizacionRequestDTO(int horizonMatches,
        Map<String, BigDecimal> modifications) {
    public SimulacionCotizacionRequestDTO {
        if (modifications == null) {
            throw new IllegalArgumentException("modifications es obligatorio.");
        }
        Map<String, BigDecimal> copia = new LinkedHashMap<>();
        modifications.forEach((metrica, valor) -> {
            if ("apps".equals(metrica) || "minsPlayed".equals(metrica)) {
                throw new IllegalArgumentException(metrica + " no es editable.");
            }
            copia.put(metrica, valor);
        });
        modifications = Map.copyOf(copia);
    }
}
