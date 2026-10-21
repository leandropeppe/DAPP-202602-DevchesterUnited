package com.devchesterunited.devchesterunitedv2.modelo.valorizacion;

import com.devchesterunited.devchesterunitedv2.modelo.MetricasEstadisticasJugador;
import java.math.BigDecimal;
import java.util.function.Function;

public enum MetricaValorizacion {
    GOALS(stats -> decimal(stats.goles())),
    ASSISTS(stats -> decimal(stats.asistencias())),
    SHOTS(MetricasEstadisticasJugador::tiros),
    KEY_PASSES(MetricasEstadisticasJugador::pasesClave),
    DRIBBLES(MetricasEstadisticasJugador::regates),
    TACKLES(MetricasEstadisticasJugador::tackles),
    RATING(MetricasEstadisticasJugador::valoracion),
    TOTAL_PASSES(stats -> stats.pases().totalPassesPerGame()),
    PASS_SUCCESS(stats -> stats.pases().passSuccess()),
    INTERCEPTIONS(stats -> stats.defensivas().interceptionPerGame()),
    CLEARANCES(stats -> stats.defensivas().clearancePerGame()),
    BLOCKS(stats -> stats.defensivas().outfielderBlockPerGame()),
    LONG_PASSES(stats -> stats.pases().accurateLongPassPerGame()),
    MAN_OF_THE_MATCH(stats -> decimal(stats.generales().manOfTheMatch())),
    FOULS(stats -> stats.defensivas().foulsPerGame()),
    YELLOW_CARDS(stats -> decimal(stats.generales().yellowCard())),
    RED_CARDS(stats -> decimal(stats.generales().redCard())),
    DISPOSSESSED(stats -> stats.ofensivas().dispossessedPerGame()),
    OFFSIDES_GIVEN(stats -> stats.ofensivas().offsideGivenPerGame()),
    WAS_DRIBBLED(stats -> stats.defensivas().wasDribbledPerGame()),
    AERIAL_WON(stats -> stats.generales().aerialWonPerGame()),
    FOUL_GIVEN(stats -> stats.ofensivas().foulGivenPerGame()),
    TURNOVER(stats -> stats.ofensivas().turnoverPerGame()),
    OFFSIDES_WON(stats -> stats.defensivas().offsideWonPerGame()),
    GOAL_OWN(stats -> decimal(stats.defensivas().goalOwn())),
    ACCURATE_CROSSES(stats -> stats.pases().accurateCrossesPerGame()),
    THROUGH_BALLS(stats -> stats.pases().accurateThroughBallPerGame());

    private final Function<MetricasEstadisticasJugador, BigDecimal> extractor;

    MetricaValorizacion(Function<MetricasEstadisticasJugador, BigDecimal> extractor) {
        this.extractor = extractor;
    }

    public BigDecimal extraer(MetricasEstadisticasJugador stats) {
        return extractor.apply(stats);
    }

    private static BigDecimal decimal(Integer value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}
