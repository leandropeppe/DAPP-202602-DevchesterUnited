package com.devchesterunited.devchesterunitedv2.modelo.valorizacion;

import static org.assertj.core.api.Assertions.assertThat;

import com.devchesterunited.devchesterunitedv2.valorizacion.ValorizacionFixtures;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class EstrategiaPosicionalV2Test {
    private final EstrategiaPosicionalV2 estrategia = new EstrategiaPosicionalV2();
    private final MotorValorizacion motor = new MotorValorizacion();

    @Test
    void defineBloquesSinRatingNiMercadoYConfiabilidadProgresiva() {
        assertThat(estrategia.version()).isEqualTo(2);
        assertThat(estrategia.factorMercadoPosicion("Forward")).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(estrategia.pesosPositivos("Forward")).doesNotContainKey(MetricaValorizacion.RATING);
        assertThat(estrategia.confiabilidad(600)).isEqualByComparingTo("0.66666667");
        assertThat(estrategia.confiabilidad(null)).isZero();
    }

    @Test
    void calculaScoreAcotadoUsaSigmoideYToleraMetricasAusentes() {
        JugadorEstadistico completo = ValorizacionFixtures.jugador("Forward", 8, new BigDecimal("8.5"));
        JugadorEstadistico bajo = ValorizacionFixtures.jugador("Forward", 1, new BigDecimal("7.0"));
        var resultados = motor.calcular(estrategia, List.of(completo, bajo), ConfiguracionElegibilidad.sinUmbral());

        assertThat(resultados).hasSize(2);
        assertThat(resultados).allSatisfy(r -> {
            assertThat(r.score()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
            assertThat(r.valor()).isBetween(BigDecimal.ONE, BigDecimal.valueOf(100));
            assertThat(r.normalizaciones()).allSatisfy(n -> assertThat(n.metodo()).isEqualTo("Z_SCORE_SIGMOIDE"));
        });
    }

    @Test
    void desviacionCeroProduceValorNeutro() {
        JugadorEstadistico uno = ValorizacionFixtures.jugador("Midfielder", 2, new BigDecimal("8"));
        JugadorEstadistico dos = ValorizacionFixtures.jugador("Midfielder", 2, new BigDecimal("7"));
        var resultados = motor.calcular(estrategia, List.of(uno, dos), ConfiguracionElegibilidad.sinUmbral());

        assertThat(resultados).isNotEmpty();
        assertThat(resultados).allSatisfy(r -> assertThat(r.normalizaciones()).anyMatch(n -> n.estado() == ResultadoNormalizacion.Estado.CONSTANTE));
    }
}
