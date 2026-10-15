package com.devchesterunited.devchesterunitedv2.service.dto;

import java.math.BigDecimal;

public record EstadisticasJugadorResultado(
        String posicion,
        Integer minutosJugados,
        Integer goles,
        Integer asistencias,
        BigDecimal tiros,
        BigDecimal pasesClave,
        BigDecimal regates,
        BigDecimal tackles,
        BigDecimal valoracion,
        Integer tarjetasAmarillas,
        Integer tarjetasRojas,
        Generales generales,
        Ofensivas ofensivas,
        Defensivas defensivas,
        Pases pases) {
    public EstadisticasJugadorResultado(String posicion, Integer minutosJugados, Integer goles, Integer asistencias,
            BigDecimal tiros, BigDecimal pasesClave, BigDecimal regates, BigDecimal tackles, BigDecimal valoracion,
            Integer tarjetasAmarillas, Integer tarjetasRojas) {
        this(posicion, minutosJugados, goles, asistencias, tiros, pasesClave, regates, tackles, valoracion,
                tarjetasAmarillas, tarjetasRojas, null, null, null, null);
    }
    public record Generales(Integer apps, Integer subOn, Integer minsPlayed, Integer manOfTheMatch,
            BigDecimal aerialWonPerGame, BigDecimal rating, Integer yellowCard, Integer redCard) { }
    public record Ofensivas(Integer goal, Integer assistTotal, BigDecimal shotsPerGame, BigDecimal keyPassPerGame,
            BigDecimal dribbleWonPerGame, BigDecimal foulGivenPerGame, BigDecimal offsideGivenPerGame,
            BigDecimal dispossessedPerGame, BigDecimal turnoverPerGame) { }
    public record Defensivas(BigDecimal tacklePerGame, BigDecimal interceptionPerGame, BigDecimal foulsPerGame,
            BigDecimal offsideWonPerGame, BigDecimal clearancePerGame, BigDecimal wasDribbledPerGame,
            BigDecimal outfielderBlockPerGame, Integer goalOwn) { }
    public record Pases(BigDecimal totalPassesPerGame, BigDecimal passSuccess, BigDecimal accurateCrossesPerGame,
            BigDecimal accurateLongPassPerGame, BigDecimal accurateThroughBallPerGame) { }
}
