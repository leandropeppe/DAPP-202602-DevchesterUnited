package com.devchesterunited.devchesterunitedv2.unit.simulacion;

import com.devchesterunited.devchesterunitedv2.modelo.simulacion.ResolutorPosicionSimulacion;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaPosicionalV2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResolucionPosicionSimulacionTest {
    private final ResolutorPosicionSimulacion resolver = new ResolutorPosicionSimulacion();
    private final EstrategiaPosicionalV2 estrategia = new EstrategiaPosicionalV2();

    @Test
    void normalizaUnaPosicionDeterministicamente() {
        var result = resolver.resolver("FW", estrategia);
        assertTrue(result.resoluble());
        assertEquals("Forward", result.aplicada());
    }

    @Test
    void bloqueaPosicionesMultiplesAmbiguas() {
        assertEquals("POSICION_AMBIGUA", resolver.resolver("Forward/Midfielder", estrategia).blockingReason());
    }

    @Test
    void diferenciaPosicionNoSoportada() {
        assertEquals("POSICION_NO_SOPORTADA", resolver.resolver("Coach", estrategia).blockingReason());
    }
}
