package com.devchesterunited.devchesterunitedv2.modelo.valorizacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EstrategiaValorizacionTest {
    @Test
    void registraDosEstrategiasHabilitadasYPositionalVisible() {
        EstrategiaGeneralV1 general = new EstrategiaGeneralV1(new BigDecimal("0"), new BigDecimal("1"));
        EstrategiaPosicionalV1 positional = new EstrategiaPosicionalV1(new BigDecimal("0"), new BigDecimal("1"));
        assertEquals(0, BigDecimal.ONE.compareTo(general.pesosPositivos("Forward").values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)));
        assertEquals(0, BigDecimal.ONE.compareTo(positional.pesosPositivos("Defender").values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)));
        assertEquals("POSITIONAL", new com.devchesterunited.devchesterunitedv2.service.config.ValorizacionInicialConfig()
                .estrategiasIniciales(new BigDecimal("0"), BigDecimal.ONE, new BigDecimal("0"), BigDecimal.ONE)
                .estrategiaVisibleId());
    }

    @Test
    void rechazaPesosQueNoSumanCienPorCiento() {
        var pesos = Map.of(MetricaValorizacion.RATING, new BigDecimal("0.9"));
        var penalizaciones = Map.<MetricaValorizacion, BigDecimal>of();
        assertThrows(IllegalArgumentException.class, () -> new ConfiguracionEstrategia(
                "INVALID", 1, TipoEstrategiaValorizacion.GENERAL, false,
                pesos, penalizaciones,
                BigDecimal.ONE, BigDecimal.ONE));
    }
}
