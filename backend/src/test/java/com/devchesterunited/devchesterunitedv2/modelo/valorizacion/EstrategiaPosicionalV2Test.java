package com.devchesterunited.devchesterunitedv2.modelo.valorizacion;

import static org.assertj.core.api.Assertions.assertThat;

import com.devchesterunited.devchesterunitedv2.valorizacion.ValorizacionFixtures;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasDefensivas;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasGenerales;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasOfensivas;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasPases;
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
        assertThat(estrategia.pendienteSigmoide()).isEqualByComparingTo("0.65");
        assertThat(estrategia.limiteNormalizacionManOfTheMatch()).isEqualByComparingTo("0.75");
    }

    @Test
    void calculaScoreAcotadoUsaSigmoideYToleraMetricasAusentes() {
        JugadorEstadistico completo = ValorizacionFixtures.jugador("Forward", 8, new BigDecimal("8.5"));
        JugadorEstadistico bajo = ValorizacionFixtures.jugador("Forward", 1, new BigDecimal("7.0"));
        var resultados = motor.calcular(estrategia, List.of(completo, bajo), ConfiguracionElegibilidad.sinUmbral());

        assertThat(resultados).hasSize(2).allSatisfy(r -> assertThat(r).matches(
                resultado -> resultado.score().compareTo(BigDecimal.ZERO) >= 0
                        && resultado.score().compareTo(BigDecimal.ONE) <= 0
                        && resultado.valor().compareTo(BigDecimal.ONE) >= 0
                        && resultado.valor().compareTo(BigDecimal.valueOf(100)) <= 0
                        && resultado.normalizaciones().stream()
                                .allMatch(n -> n.metodo().contains("Z_SCORE_SIGMOIDE_K_0_65"))));
    }

    @Test
    void limitaManOfTheMatchSinModificarValoresModerados() {
        var cero = defensor(0, bd("0"), bd("0"), bd("0"), bd("0"), bd("0"));
        var uno = defensor(0, bd("1"), bd("1"), bd("1"), bd("1"), bd("1"));
        var dos = defensor(0, bd("2"), bd("2"), bd("2"), bd("2"), bd("2"));
        var maximo = defensor(10, bd("3"), bd("3"), bd("3"), bd("3"), bd("3"));

        var resultados = motor.calcular(estrategia, List.of(cero, uno, dos, maximo), ConfiguracionElegibilidad.sinUmbral());
        var normalizaciones = resultados.get(3).normalizaciones();
        var motm = normalizaciones.stream().filter(n -> n.metrica() == MetricaValorizacion.MAN_OF_THE_MATCH).findFirst().orElseThrow();
        assertThat(motm.valorNormalizado()).isLessThanOrEqualTo(estrategia.limiteNormalizacionManOfTheMatch());

        var moderado = resultados.get(1).normalizaciones().stream()
                .filter(n -> n.metrica() == MetricaValorizacion.MAN_OF_THE_MATCH).findFirst().orElseThrow();
        assertThat(moderado.valorNormalizado()).isLessThan(estrategia.limiteNormalizacionManOfTheMatch());
    }

    @Test
    void trazaAmenazaDefensivaIdentificaLasCuatroSubfamilias() {
        var uno = defensor(1, bd("1"), bd("1"), bd("1"), bd("1"), bd("1"));
        var dos = defensor(2, bd("2"), bd("2"), bd("2"), bd("2"), bd("2"));

        var resultado = motor.calcular(estrategia, List.of(uno, dos), ConfiguracionElegibilidad.sinUmbral()).get(0);

        assertThat(resultado.normalizaciones()).anyMatch(n -> n.metodo().contains("CONTENCION"));
        assertThat(resultado.normalizaciones()).anyMatch(n -> n.metodo().contains("DESPEJES"));
        assertThat(resultado.normalizaciones()).anyMatch(n -> n.metodo().contains("AEREO"));
        assertThat(resultado.normalizaciones()).anyMatch(n -> n.metodo().contains("LECTURA_LINEA"));
    }

    @Test
    void subfamiliasAusentesNoAnulanLasSubfamiliasDisponiblesYSinMetricasUsaFallback() {
        var completo = defensor(1, bd("1"), bd("1"), bd("1"), bd("1"), bd("1"));
        var sinContencion = new JugadorEstadistico(UUID.randomUUID(), "Defender", new MetricasEstadisticasJugador(
                new MetricasGenerales(10, 0, 900, 0, bd("1"), bd("7"), 0, 0),
                new MetricasOfensivas(1, 1, bd("3"), bd("2"), bd("1"), bd("1"), bd("1"), bd("1"), bd("1")),
                new MetricasDefensivas(null, null, bd("1"), bd("1"), bd("3"), bd("0"), null, 0),
                new MetricasPases(bd("50"), bd("80"), bd("1"), bd("4"), bd("1"))));

        var resultado = motor.calcular(estrategia, List.of(completo, sinContencion), ConfiguracionElegibilidad.sinUmbral());
        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(1).normalizaciones()).anyMatch(n -> n.metodo().contains("CONTENCION")
                && n.estado() == ResultadoNormalizacion.Estado.AUSENTE);

        var sinDefensa = new JugadorEstadistico(UUID.randomUUID(), "Defender", new MetricasEstadisticasJugador(
                new MetricasGenerales(10, 0, 900, 0, null, bd("7"), 0, 0),
                null, null, null));
        var fallback = motor.calcular(estrategia, List.of(sinDefensa), ConfiguracionElegibilidad.sinUmbral()).get(0);
        assertThat(fallback.normalizaciones()).anyMatch(n -> n.metrica() == MetricaValorizacion.TACKLES
                && n.estado() == ResultadoNormalizacion.Estado.AUSENTE);
        assertThat(fallback.score()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
    }

    @Test
    void desviacionCeroProduceValorNeutro() {
        JugadorEstadistico uno = ValorizacionFixtures.jugador("Midfielder", 2, new BigDecimal("8"));
        JugadorEstadistico dos = ValorizacionFixtures.jugador("Midfielder", 2, new BigDecimal("7"));
        var resultados = motor.calcular(estrategia, List.of(uno, dos), ConfiguracionElegibilidad.sinUmbral());

        assertThat(resultados).isNotEmpty().allSatisfy(r -> assertThat(r.normalizaciones())
                .anyMatch(n -> n.estado() == ResultadoNormalizacion.Estado.CONSTANTE));
    }

    private static JugadorEstadistico defensor(int motm, BigDecimal tackles, BigDecimal interceptions,
            BigDecimal blocks, BigDecimal clearances, BigDecimal aerial) {
        return new JugadorEstadistico(UUID.randomUUID(), "Defender", new MetricasEstadisticasJugador(
                new MetricasGenerales(10, 0, 900, motm, aerial, bd("7"), 0, 0),
                new MetricasOfensivas(1, 1, bd("3"), bd("2"), bd("1"), bd("1"), bd("1"), bd("1"), bd("1")),
                new MetricasDefensivas(tackles, interceptions, bd("1"), bd("1"), clearances, bd("0"), blocks, 0),
                new MetricasPases(bd("50"), bd("80"), bd("1"), bd("4"), bd("1"))));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
