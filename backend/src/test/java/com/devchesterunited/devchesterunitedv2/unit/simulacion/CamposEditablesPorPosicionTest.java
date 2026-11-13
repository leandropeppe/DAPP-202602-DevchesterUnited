package com.devchesterunited.devchesterunitedv2.unit.simulacion;

import com.devchesterunited.devchesterunitedv2.service.impl.simulacion.ConfiguradorMetricasSimulacion;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaPosicionalV2;
import com.devchesterunited.devchesterunitedv2.support.fixtures.valorizacion.SimulacionPosicionesFixtures;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CamposEditablesPorPosicionTest {
    private final ConfiguradorMetricasSimulacion configurador = new ConfiguradorMetricasSimulacion();
    private final EstrategiaPosicionalV2 estrategia = new EstrategiaPosicionalV2();

    @Test
    void forwardSoloExponeLaWhitelistFuncionalYRatingEsEditable() {
        assertEquals(Set.of("GOALS", "ASSISTS", "RATING", "SHOTS", "KEY_PASSES", "MAN_OF_THE_MATCH"),
                editables("Forward", SimulacionPosicionesFixtures.forward().metricas()));
    }

    @Test
    void midfielderSoloExponeLaWhitelistFuncional() {
        assertEquals(Set.of("ASSISTS", "RATING", "KEY_PASSES", "PASS_SUCCESS", "GOALS", "MAN_OF_THE_MATCH"),
                editables("Midfielder", SimulacionPosicionesFixtures.midfielder().metricas()));
    }

    @Test
    void defenderSoloExponeLaWhitelistFuncional() {
        assertEquals(Set.of("GOALS", "RATING", "TACKLES", "INTERCEPTIONS", "CLEARANCES", "AERIAL_WON"),
                editables("Defender", SimulacionPosicionesFixtures.defender().metricas()));
    }

    private Set<String> editables(String posicion, com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador metricas) {
        return configurador.configurar(posicion, estrategia, metricas).stream()
                .filter(dto -> dto.editable()).map(dto -> dto.metric()).collect(Collectors.toSet());
    }
}
