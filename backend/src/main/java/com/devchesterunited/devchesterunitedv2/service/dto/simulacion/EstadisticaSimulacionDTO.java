package com.devchesterunited.devchesterunitedv2.service.dto.simulacion;

import java.math.BigDecimal;

public record EstadisticaSimulacionDTO(String metric, String label, TipoMetricaSimulacionDTO kind,
        BigDecimal currentValue, boolean editable, BigDecimal min, BigDecimal max) {
}
