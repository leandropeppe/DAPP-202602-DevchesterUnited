package com.devchesterunited.devchesterunitedv2.unit.valorizacion;

import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.MetricaValorizacion;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfiguracionEstrategiaTest {
    @Test
    void rechazaPesosQueNoSumanCienPorCiento() {
        var pesos = Map.of(MetricaValorizacion.GOALS, new BigDecimal(".99"));
        var penalizaciones = Map.<MetricaValorizacion, BigDecimal>of();
        assertThrows(IllegalArgumentException.class, () -> new ConfiguracionEstrategia("GENERAL", 1,
                TipoEstrategiaValorizacion.GENERAL, false, pesos, penalizaciones, BigDecimal.ZERO, BigDecimal.ONE));
    }

    @Test
    void rechazaMetricasNulasYPenalizacionesNegativas() {
        var penalizacionesVacias = Map.<MetricaValorizacion, BigDecimal>of();
        assertThrows(IllegalArgumentException.class, () -> new ConfiguracionEstrategia("GENERAL", 1,
                TipoEstrategiaValorizacion.GENERAL, false, null, penalizacionesVacias, BigDecimal.ZERO, BigDecimal.ONE));
        var pesos = Map.of(MetricaValorizacion.GOALS, BigDecimal.ONE);
        var penalizaciones = Map.of(MetricaValorizacion.RED_CARDS, new BigDecimal("-.01"));
        assertThrows(IllegalArgumentException.class, () -> new ConfiguracionEstrategia("GENERAL", 1,
                TipoEstrategiaValorizacion.GENERAL, false,
                pesos, penalizaciones, BigDecimal.ZERO, BigDecimal.ONE));
    }
}
