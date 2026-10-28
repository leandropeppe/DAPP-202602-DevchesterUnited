package com.devchesterunited.devchesterunitedv2.unit.simulacion;

import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.JugadorEstadistico;
import com.devchesterunited.devchesterunitedv2.service.impl.simulacion.JugadorEstadisticoSimulacionAdapter;
import com.devchesterunited.devchesterunitedv2.support.fixtures.valorizacion.SimulacionFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class JugadorEstadisticoSimulacionTest {
    @Test
    void conservaIdentidadPosicionYFamiliasDeMetricas() {
        var proyectadas = SimulacionFixtures.estadisticasProyectadas();

        JugadorEstadistico adaptado = JugadorEstadisticoSimulacionAdapter.adaptar(
                SimulacionFixtures.JUGADOR_DELANTERO_ID, "Forward", proyectadas);

        assertEquals(SimulacionFixtures.JUGADOR_DELANTERO_ID, adaptado.jugadorId());
        assertEquals("Forward", adaptado.posicion());
        assertSame(proyectadas.metricas(), adaptado.metricas());
        assertEquals(8, adaptado.metricas().ofensivas().goal());
        assertEquals(15, adaptado.metricas().generales().apps());
        assertEquals(1350, adaptado.metricas().generales().minsPlayed());
        assertEquals(82, adaptado.metricas().pases().passSuccess().intValue());
    }
}
