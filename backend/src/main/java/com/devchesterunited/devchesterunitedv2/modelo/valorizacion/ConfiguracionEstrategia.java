package com.devchesterunited.devchesterunitedv2.modelo.valorizacion;

import java.math.BigDecimal;
import java.util.Map;

public record ConfiguracionEstrategia(String id, int version, TipoEstrategiaValorizacion tipo,
        boolean esProvisoria, Map<MetricaValorizacion, BigDecimal> pesos,
        Map<MetricaValorizacion, BigDecimal> penalizaciones,
        BigDecimal valorBase, BigDecimal factorEscala) {
    public ConfiguracionEstrategia {
        if (id == null || id.isBlank() || version <= 0 || tipo == null) {
            throw new IllegalArgumentException("La identificación de la estrategia es inválida.");
        }
        if (pesos == null || penalizaciones == null || pesos.entrySet().stream()
                .anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)
                || penalizaciones.entrySet().stream()
                .anyMatch(entry -> entry.getKey() == null || entry.getValue() == null
                        || entry.getValue().signum() < 0)) {
            throw new IllegalArgumentException("La configuración de métricas es inválida.");
        }
        pesos = Map.copyOf(pesos);
        penalizaciones = Map.copyOf(penalizaciones);
        if (pesos.values().stream().anyMatch(value -> value.signum() < 0)
                || pesos.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("Los pesos positivos deben sumar 100%.");
        }
        if (valorBase == null || factorEscala == null || valorBase.signum() < 0 || factorEscala.signum() < 0) {
            throw new IllegalArgumentException("La conversión de cotización es inválida.");
        }
    }
}
