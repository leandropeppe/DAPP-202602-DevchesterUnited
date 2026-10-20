package com.devchesterunited.devchesterunitedv2.modelo.valorizacion;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfiguracionEstrategiaTest {
    @Test
    void rechazaPesosQueNoSumanCienPorCiento() {
        assertThrows(IllegalArgumentException.class, () -> new ConfiguracionEstrategia("GENERAL", 1,
                TipoEstrategiaValorizacion.GENERAL, false,
                Map.of(MetricaValorizacion.GOALS, new BigDecimal(".99")), Map.of(), BigDecimal.ZERO, BigDecimal.ONE));
    }

    @Test
    void rechazaMetricasNulasYPenalizacionesNegativas() {
        assertThrows(IllegalArgumentException.class, () -> new ConfiguracionEstrategia("GENERAL", 1,
                TipoEstrategiaValorizacion.GENERAL, false, null, Map.of(), BigDecimal.ZERO, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new ConfiguracionEstrategia("GENERAL", 1,
                TipoEstrategiaValorizacion.GENERAL, false,
                Map.of(MetricaValorizacion.GOALS, BigDecimal.ONE),
                Map.of(MetricaValorizacion.RED_CARDS, new BigDecimal("-.01")), BigDecimal.ZERO, BigDecimal.ONE));
    }
}
