package com.devchesterunited.devchesterunitedv2.unit.simulacion;

import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.MotorValorizacion;
import com.devchesterunited.devchesterunitedv2.service.impl.simulacion.JugadorEstadisticoSimulacionAdapter;
import com.devchesterunited.devchesterunitedv2.service.impl.simulacion.EstrategiaSimulacionResolver;
import com.devchesterunited.devchesterunitedv2.service.impl.simulacion.MotorSimulacionEnMemoria;
import com.devchesterunited.devchesterunitedv2.service.impl.simulacion.PoblacionElegibleSimulacionReader;
import com.devchesterunited.devchesterunitedv2.support.fixtures.valorizacion.SimulacionFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculoSimulacionMotorEquivalenciaTest {
    @Test
    void coincideConLaInvocacionDirectaDelMotorEnTodosLosResultadosRelevantes() {
        var estrategia = SimulacionFixtures.estrategia();
        var configuracion = SimulacionFixtures.elegibilidad();
        var resolver = new EstrategiaSimulacionResolver(
                new com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiasIniciales(
                        List.of(estrategia), estrategia.id(), estrategia.version()), configuracion);
        EstrategiaSimulacionResolver.Resolucion resolucion = resolver.resolverVisible();
        PoblacionElegibleSimulacionReader reader = new PoblacionElegibleSimulacionReader(
                SimulacionFixtures::poblacionOficial, new MotorValorizacion());
        var proyectadas = SimulacionFixtures.estadisticasProyectadas();
        var motorSimulacion = new MotorSimulacionEnMemoria(new MotorValorizacion(), reader);

        var simulado = motorSimulacion.simular(SimulacionFixtures.JUGADOR_DELANTERO_ID, "Forward", 5,
                proyectadas, resolucion);
        var calculadoPorSimulacion = motorSimulacion.calcular(SimulacionFixtures.JUGADOR_DELANTERO_ID,
                "Forward", proyectadas, resolucion);
        var escenario = JugadorEstadisticoSimulacionAdapter.adaptar(
                SimulacionFixtures.JUGADOR_DELANTERO_ID, "Forward", proyectadas);
        var poblacion = reader.leer(estrategia, configuracion, escenario);
        var directo = new MotorValorizacion().calcular(estrategia, poblacion, configuracion).stream()
                .filter(resultado -> resultado.jugadorId().equals(SimulacionFixtures.JUGADOR_DELANTERO_ID))
                .findFirst().orElseThrow();

        assertEquals(directo.score(), simulado.score());
        assertEquals(directo.valor(), simulado.cotizacionSimulada());
        assertEquals(directo.score(), calculadoPorSimulacion.score());
        assertEquals(directo.valor(), calculadoPorSimulacion.valor());
        assertEquals(directo.normalizaciones(), calculadoPorSimulacion.normalizaciones());
        assertEquals(directo.penalizacionesAplicadas(), calculadoPorSimulacion.penalizacionesAplicadas());
        assertEquals(directo.elegibilidad(), calculadoPorSimulacion.elegibilidad());
    }
}
