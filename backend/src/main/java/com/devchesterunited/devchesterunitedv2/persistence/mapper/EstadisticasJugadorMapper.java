package com.devchesterunited.devchesterunitedv2.persistence.mapper;

import com.devchesterunited.devchesterunitedv2.modelo.MetricasDefensivas;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasGenerales;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasOfensivas;
import com.devchesterunited.devchesterunitedv2.modelo.MetricasPases;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EstadisticasJugadorEntity;

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
