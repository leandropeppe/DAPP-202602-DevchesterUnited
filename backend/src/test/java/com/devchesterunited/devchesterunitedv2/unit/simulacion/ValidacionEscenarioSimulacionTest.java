package com.devchesterunited.devchesterunitedv2.unit.simulacion;

import com.devchesterunited.devchesterunitedv2.modelo.simulacion.CodigoErrorSimulacion;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.ExcepcionSimulacion;
import com.devchesterunited.devchesterunitedv2.modelo.simulacion.EscenarioSimulacion;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.MetricaValorizacion;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidacionEscenarioSimulacionTest {
    @Test
    void aceptaUnicamenteLosHorizontesPermitidos() {
        for (int horizonte : new int[] { 3, 5, 8, 10 }) {
            new EscenarioSimulacion(UUID.randomUUID(), horizonte, Map.of());
        }
        UUID playerId = UUID.randomUUID();
        Map<MetricaValorizacion, BigDecimal> modificaciones = Map.of();
        ExcepcionSimulacion error = assertThrows(ExcepcionSimulacion.class,
                () -> new EscenarioSimulacion(playerId, 4, modificaciones));
        assertEquals(CodigoErrorSimulacion.HORIZONTE_INVALIDO, error.codigo());
    }

    @Test
    void rechazaNegativosPorcentajesRatingsYMetricasAusentes() {
        assertCodigo(Map.of(MetricaValorizacion.GOALS, BigDecimal.valueOf(-1)), CodigoErrorSimulacion.VALOR_FUERA_DE_RANGO);
        assertCodigo(Map.of(MetricaValorizacion.PASS_SUCCESS, BigDecimal.valueOf(101)), CodigoErrorSimulacion.VALOR_FUERA_DE_RANGO);
        assertCodigo(Map.of(MetricaValorizacion.RATING, BigDecimal.valueOf(10.01)), CodigoErrorSimulacion.VALOR_FUERA_DE_RANGO);
        Map<MetricaValorizacion, BigDecimal> nulo = new HashMap<>();
        nulo.put(MetricaValorizacion.SHOTS, null);
        assertCodigo(nulo, CodigoErrorSimulacion.VALOR_FUERA_DE_RANGO);
    }

    private static void assertCodigo(Map<MetricaValorizacion, BigDecimal> modificaciones,
            CodigoErrorSimulacion codigo) {
        UUID playerId = UUID.randomUUID();
        ExcepcionSimulacion error = assertThrows(ExcepcionSimulacion.class,
                () -> new EscenarioSimulacion(playerId, 5, modificaciones));
        assertEquals(codigo, error.codigo());
    }
}
