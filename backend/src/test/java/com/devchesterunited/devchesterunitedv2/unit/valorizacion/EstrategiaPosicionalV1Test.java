package com.devchesterunited.devchesterunitedv2.unit.valorizacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaPosicionalV1;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.MetricaValorizacion;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.*;

import java.math.BigDecimal;
import java.util.Map;


class EstrategiaPosicionalV1Test {
    private final EstrategiaPosicionalV1 estrategia = new EstrategiaPosicionalV1(BigDecimal.ZERO, BigDecimal.ONE);

    @Test
    void conservaPesosExactosPorPosicionYProvisionalidadSoloEnPo() {
        assertEquals(Map.of(GOALS(), d(".35"), ASSISTS(), d(".10"), SHOTS(), d(".18"), KEY_PASSES(), d(".12"), DRIBBLES(), d(".10"), RATING(), d(".15")), estrategia.pesosPositivos("DL"));
        assertEquals(Map.of(GOALS(), d(".10"), ASSISTS(), d(".15"), KEY_PASSES(), d(".20"), TOTAL_PASSES(), d(".15"), PASS_SUCCESS(), d(".14"), DRIBBLES(), d(".08"), TACKLES(), d(".08"), RATING(), d(".10")), estrategia.pesosPositivos("MC"));
        assertEquals(Map.of(GOALS(), d(".12"), TACKLES(), d(".20"), INTERCEPTIONS(), d(".19"), CLEARANCES(), d(".12"), BLOCKS(), d(".07"), TOTAL_PASSES(), d(".08"), PASS_SUCCESS(), d(".07"), RATING(), d(".15")), estrategia.pesosPositivos("DF"));
        assertEquals(Map.of(RATING(), d(".30"), PASS_SUCCESS(), d(".30"), LONG_PASSES(), d(".25"), MAN_OF_THE_MATCH(), d(".15")), estrategia.pesosPositivos("PO"));
        for (String posicion : new String[]{"DL", "MC", "DF", "PO"}) {
            assertEquals(0, estrategia.pesosPositivos(posicion).values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(BigDecimal.ONE));
        }
        assertFalse(estrategia.esProvisoria());
        assertTrue(estrategia.esProvisoria("PO"));
        assertFalse(estrategia.esProvisoria("DF"));
    }

    @Test
    void exponeFactoresDeMercadoConfigurablesYMapeaPosicionesExternas() {
        assertEquals(d("1.15"), estrategia.factorMercadoPosicion("Forward"));
        assertEquals(d("1.05"), estrategia.factorMercadoPosicion("Midfielder"));
        assertEquals(d("0.95"), estrategia.factorMercadoPosicion("Defender"));
        assertEquals(d("0.90"), estrategia.factorMercadoPosicion("Goalkeeper"));
        assertEquals("DL", estrategia.posicionNormalizadaParaFactor("Forward"));
        assertEquals("MC", estrategia.posicionNormalizadaParaFactor("Midfielder"));
        assertEquals("DF", estrategia.posicionNormalizadaParaFactor("Defender"));
        assertEquals("PO", estrategia.posicionNormalizadaParaFactor("Goalkeeper"));
    }

    private static MetricaValorizacion GOALS() { return MetricaValorizacion.GOALS; }
    private static MetricaValorizacion ASSISTS() { return MetricaValorizacion.ASSISTS; }
    private static MetricaValorizacion SHOTS() { return MetricaValorizacion.SHOTS; }
    private static MetricaValorizacion KEY_PASSES() { return MetricaValorizacion.KEY_PASSES; }
    private static MetricaValorizacion DRIBBLES() { return MetricaValorizacion.DRIBBLES; }
    private static MetricaValorizacion TACKLES() { return MetricaValorizacion.TACKLES; }
    private static MetricaValorizacion RATING() { return MetricaValorizacion.RATING; }
    private static MetricaValorizacion TOTAL_PASSES() { return MetricaValorizacion.TOTAL_PASSES; }
    private static MetricaValorizacion PASS_SUCCESS() { return MetricaValorizacion.PASS_SUCCESS; }
    private static MetricaValorizacion INTERCEPTIONS() { return MetricaValorizacion.INTERCEPTIONS; }
    private static MetricaValorizacion CLEARANCES() { return MetricaValorizacion.CLEARANCES; }
    private static MetricaValorizacion BLOCKS() { return MetricaValorizacion.BLOCKS; }
    private static MetricaValorizacion LONG_PASSES() { return MetricaValorizacion.LONG_PASSES; }
    private static MetricaValorizacion MAN_OF_THE_MATCH() { return MetricaValorizacion.MAN_OF_THE_MATCH; }
    private static BigDecimal d(String value) { return new BigDecimal(value); }
}
