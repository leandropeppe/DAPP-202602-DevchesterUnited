package com.devchesterunited.devchesterunitedv2.modelo.valorizacion;

public record ConfiguracionElegibilidad(Integer minimoPartidos, Integer minimoMinutos) {
    public ConfiguracionElegibilidad {
        if (minimoPartidos != null && minimoPartidos < 0) {
            throw new IllegalArgumentException("El mínimo de partidos no puede ser negativo.");
        }
        if (minimoMinutos != null && minimoMinutos < 0) {
            throw new IllegalArgumentException("El mínimo de minutos no puede ser negativo.");
        }
    }

    public static ConfiguracionElegibilidad sinUmbral() {
        return new ConfiguracionElegibilidad(null, null);
    }
}
