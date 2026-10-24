package com.devchesterunited.devchesterunitedv2.configuration.valorizacion;

import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.ConfiguracionElegibilidad;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ValorizacionEjecucionConfig {
    private final boolean configurada;
    private final String minimoPartidos;
    private final String minimoMinutos;

    public ValorizacionEjecucionConfig(
            @Value("${valorizacion.elegibilidad.configurada:false}") boolean configurada,
            @Value("${valorizacion.elegibilidad.minimo-partidos:}") String minimoPartidos,
            @Value("${valorizacion.elegibilidad.minimo-minutos:}") String minimoMinutos) {
        this.configurada = configurada;
        this.minimoPartidos = minimoPartidos;
        this.minimoMinutos = minimoMinutos;
    }

    public ConfiguracionElegibilidad configuracionElegibilidad() {
        if (!configurada) throw new IllegalStateException("La configuración de elegibilidad no está disponible.");
        return new ConfiguracionElegibilidad(parsear(minimoPartidos, "minimo-partidos"), parsear(minimoMinutos, "minimo-minutos"));
    }

    private static Integer parsear(String valor, String nombre) {
        if (valor == null || valor.isBlank()) return null;
        try { return Integer.valueOf(valor); }
        catch (NumberFormatException exception) { throw new IllegalStateException("La configuración " + nombre + " no es válida.", exception); }
    }
}
