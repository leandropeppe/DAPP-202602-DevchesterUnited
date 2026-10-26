package com.devchesterunited.devchesterunitedv2.unit.valorizacion;

import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.*;

import com.devchesterunited.devchesterunitedv2.support.fixtures.valorizacion.ValorizacionFixtures;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElegibilidadValorizacionTest {
    @Test
    void metricaPositivaAusenteExcluyeSoloAlJugador() {
        MotorValorizacion motor = new MotorValorizacion();
        JugadorEstadistico jugador = new JugadorEstadistico(UUID.randomUUID(), "Forward",
                ValorizacionFixtures.metricasSinRating());
        ResultadoElegibilidad resultado = motor.evaluarElegibilidad(new EstrategiaGeneralV1(java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE), jugador,
                ConfiguracionElegibilidad.sinUmbral());
        assertFalse(resultado.elegible());
        assertTrue(resultado.causasExclusion().stream().anyMatch(causa -> causa.contains("RATING")));
    }

    @Test
    void umbralEsConfigurableSinDefault() {
        MotorValorizacion motor = new MotorValorizacion();
        JugadorEstadistico jugador = ValorizacionFixtures.jugador("Forward", 1, java.math.BigDecimal.TEN);
        assertTrue(motor.evaluarElegibilidad(new EstrategiaGeneralV1(java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE), jugador,
                ConfiguracionElegibilidad.sinUmbral()).elegible());
        assertFalse(motor.evaluarElegibilidad(new EstrategiaGeneralV1(java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE), jugador,
                new ConfiguracionElegibilidad(11, null)).elegible());
    }
}
