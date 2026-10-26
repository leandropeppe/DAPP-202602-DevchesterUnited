package com.devchesterunited.devchesterunitedv2.persistence.estadisticas.mapper;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasDefensivas;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasGenerales;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasOfensivas;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasPases;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.entity.EstadisticasJugadorEntity;

/** Traducción explícita entre las 30 columnas locales y el modelo compuesto. */
public final class EstadisticasJugadorMapper {
    private EstadisticasJugadorMapper() { }

    public static MetricasEstadisticasJugador aModelo(EstadisticasJugadorEntity entity) {
        return new MetricasEstadisticasJugador(
                new MetricasGenerales(entity.getApps(), entity.getSubOn(), entity.getMinutosJugados(),
                        entity.getManOfTheMatch(), entity.getAerialWonPerGame(), entity.getValoracion(),
                        entity.getTarjetasAmarillas(), entity.getTarjetasRojas()),
                new MetricasOfensivas(entity.getGoles(), entity.getAsistencias(), entity.getTiros(),
                        entity.getPasesClave(), entity.getRegates(), entity.getFoulGivenPerGame(),
                        entity.getOffsideGivenPerGame(), entity.getDispossessedPerGame(), entity.getTurnoverPerGame()),
                new MetricasDefensivas(entity.getTackles(), entity.getInterceptionPerGame(), entity.getFoulsPerGame(),
                        entity.getOffsideWonPerGame(), entity.getClearancePerGame(), entity.getWasDribbledPerGame(),
                        entity.getOutfielderBlockPerGame(), entity.getGoalOwn()),
                new MetricasPases(entity.getTotalPassesPerGame(), entity.getPassSuccess(),
                        entity.getAccurateCrossesPerGame(), entity.getAccurateLongPassPerGame(),
                        entity.getAccurateThroughBallPerGame()));
    }
}
