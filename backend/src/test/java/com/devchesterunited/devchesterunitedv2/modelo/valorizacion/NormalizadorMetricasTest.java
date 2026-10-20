package com.devchesterunited.devchesterunitedv2.modelo.valorizacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.devchesterunited.devchesterunitedv2.valorizacion.ValorizacionFixtures;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class NormalizadorMetricasTest {
    @Test
    void produceScoreEntreCeroYUnoConPoblacionGlobal() {
        MotorValorizacion motor = new MotorValorizacion();
        List<ResultadoValorizacion> resultados = motor.calcular(new EstrategiaGeneralV1(new BigDecimal("0"), BigDecimal.ONE),
                List.of(ValorizacionFixtures.jugador("Forward", 1, new BigDecimal("6")),
                        ValorizacionFixtures.jugador("Defender", 3, new BigDecimal("8"))),
                ConfiguracionElegibilidad.sinUmbral());
        assertEquals(2, resultados.size());
        assertTrue(resultados.stream().allMatch(resultado -> resultado.score().compareTo(BigDecimal.ZERO) >= 0
                && resultado.score().compareTo(BigDecimal.ONE) <= 0));
    }

    @Test
    void minMaxIgualesNoDividePorCero() {
        MotorValorizacion motor = new MotorValorizacion();
        List<ResultadoValorizacion> resultados = motor.calcular(new EstrategiaGeneralV1(new BigDecimal("0"), BigDecimal.ONE),
                List.of(ValorizacionFixtures.jugador("Forward", 1, new BigDecimal("6")),
                        ValorizacionFixtures.jugador("Forward", 1, new BigDecimal("6"))),
                ConfiguracionElegibilidad.sinUmbral());
        assertTrue(resultados.stream().allMatch(resultado -> resultado.score().compareTo(BigDecimal.ZERO) >= 0));
    }
}
