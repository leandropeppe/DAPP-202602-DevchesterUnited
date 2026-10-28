package com.devchesterunited.devchesterunitedv2.modelo.simulacion;

import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.MetricaValorizacion;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record EscenarioSimulacion(UUID jugadorId, int horizontePartidos,
        Map<MetricaValorizacion, BigDecimal> modificaciones) {
    private static final java.util.Set<Integer> HORIZONTES = java.util.Set.of(3, 5, 8, 10);

    public EscenarioSimulacion {
        if (jugadorId == null) {
            throw new DatosInsuficientesException("jugadorId");
        }
        if (!HORIZONTES.contains(horizontePartidos)) {
            throw new HorizonteSimulacionException(horizontePartidos);
        }
        if (modificaciones == null) {
            throw new DatosInsuficientesException("modificaciones");
        }
        Map<MetricaValorizacion, BigDecimal> copia = new LinkedHashMap<>();
        modificaciones.forEach((metrica, valor) -> validarModificacion(copia, metrica, valor));
        modificaciones = Map.copyOf(copia);
    }

    public static EscenarioSimulacion conModificacionNoTipada(UUID jugadorId, int horizonte,
            String metrica, BigDecimal valor) {
        if ("apps".equals(metrica) || "minsPlayed".equals(metrica)) {
            throw new MetricaNoEditableException(metrica);
        }
        throw new MetricaNoEditableException(metrica);
    }

    public static java.util.Set<Integer> horizontesPermitidos() {
        return HORIZONTES;
    }

    private static void validarModificacion(Map<MetricaValorizacion, BigDecimal> destino,
            MetricaValorizacion metrica, BigDecimal valor) {
        if (metrica == null) {
            throw new MetricaNoEditableException("null");
        }
        if (valor == null) {
            throw new RangoMetricaSimulacionException(metrica.name(), null);
        }
        CatalogoMetricaSimulacion.Definicion definicion = CatalogoMetricaSimulacion.definir(metrica);
        if (!definicion.acepta(valor)) {
            throw new RangoMetricaSimulacionException(metrica.name(), valor);
        }
        destino.put(metrica, valor);
    }
}
