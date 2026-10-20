package com.devchesterunited.devchesterunitedv2.modelo.valorizacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.devchesterunited.devchesterunitedv2.valorizacion.ValorizacionFixtures;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AjusteMercadoPosicionalTest {
    @Test
    void aplicaFactorAntesDeConvertirYConservaGeneralSinFactor() {
        var jugadores = List.of(ValorizacionFixtures.jugador("Forward", 1, BigDecimal.ONE),
                ValorizacionFixtures.jugador("Forward", 4, BigDecimal.TEN));
        var posicional = new MotorValorizacion().calcular(new EstrategiaPosicionalV1(BigDecimal.ZERO, BigDecimal.ONE),
                jugadores, ConfiguracionElegibilidad.sinUmbral());
        var mejor = posicional.get(1);

        assertEquals(new BigDecimal("1.15"), mejor.factorMercadoPosicion());
        assertEquals(0, mejor.score().compareTo(mejor.scorePosicionalAntesAjuste()
                .multiply(mejor.factorMercadoPosicion()).min(BigDecimal.ONE)));
        assertEquals(0, mejor.valor().compareTo(ConversorCotizacion.convertir(mejor.score(), BigDecimal.ZERO, BigDecimal.ONE)));
        assertTrue(mejor.score().compareTo(BigDecimal.ONE) <= 0);

        var general = new MotorValorizacion().calcular(new EstrategiaGeneralV1(BigDecimal.ZERO, BigDecimal.ONE),
                jugadores, ConfiguracionElegibilidad.sinUmbral()).get(1);
        assertEquals(BigDecimal.ONE, general.factorMercadoPosicion());
        assertEquals(null, general.posicionNormalizadaFactor());
    }
}
