package com.devchesterunited.devchesterunitedv2.unit.simulacion;

import com.devchesterunited.devchesterunitedv2.service.impl.simulacion.ConfiguradorMetricasSimulacion;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaPosicionalV2;
import com.devchesterunited.devchesterunitedv2.support.fixtures.valorizacion.SimulacionGoalkeeperFixtures;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfiguracionArqueroSimulacionTest {
    @Test
    void arqueroSoloExponeMetricasConfirmadasYDisponibles() {
        var config = new ConfiguradorMetricasSimulacion().configurar("Goalkeeper", new EstrategiaPosicionalV2(),
                SimulacionGoalkeeperFixtures.completo().metricas());
        assertEquals(Set.of("RATING", "PASS_SUCCESS", "LONG_PASSES", "MAN_OF_THE_MATCH"),
                config.stream().filter(dto -> dto.editable()).map(dto -> dto.metric()).collect(Collectors.toSet()));
    }

    @Test
    void arqueroSinLongPassesNoLoMarcaEditable() {
        var config = new ConfiguradorMetricasSimulacion().configurar("Goalkeeper", new EstrategiaPosicionalV2(),
                SimulacionGoalkeeperFixtures.sinLongPasses().metricas());
        var longPasses = config.stream().filter(dto -> dto.metric().equals("LONG_PASSES")).findFirst().orElseThrow();
        org.junit.jupiter.api.Assertions.assertFalse(longPasses.editable());
    }
}
