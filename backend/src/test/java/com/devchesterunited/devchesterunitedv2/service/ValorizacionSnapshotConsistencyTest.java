package com.devchesterunited.devchesterunitedv2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.ConfiguracionElegibilidad;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaGeneralV1;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.EstrategiaPosicionalV1;
import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.MotorValorizacion;
import com.devchesterunited.devchesterunitedv2.service.dto.estadisticas.SnapshotEstadistico;
import com.devchesterunited.devchesterunitedv2.valorizacion.ValorizacionFixtures;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValorizacionSnapshotConsistencyTest {
    @Test
    void dosEstrategiasCalculanSobreLaMismaListaDeSnapshot() {
        SnapshotEstadistico snapshot = new SnapshotEstadistico(Instant.parse("2026-10-17T00:00:00Z"),
                List.of(ValorizacionFixtures.jugador("Forward", 4, java.math.BigDecimal.TEN),
                        ValorizacionFixtures.jugador("Defender", 1, java.math.BigDecimal.ONE)));
        MotorValorizacion motor = new MotorValorizacion();
        assertEquals(2, motor.calcular(new EstrategiaGeneralV1(new java.math.BigDecimal("0"), new java.math.BigDecimal("1")), snapshot.jugadores(), ConfiguracionElegibilidad.sinUmbral()).size());
        assertEquals(2, motor.calcular(new EstrategiaPosicionalV1(new java.math.BigDecimal("0"), new java.math.BigDecimal("1")), snapshot.jugadores(), ConfiguracionElegibilidad.sinUmbral()).size());
    }
}
