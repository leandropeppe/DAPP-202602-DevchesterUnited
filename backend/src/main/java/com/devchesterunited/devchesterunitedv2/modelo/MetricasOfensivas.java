package com.devchesterunited.devchesterunitedv2.modelo;
import java.math.BigDecimal;
public record MetricasOfensivas(Integer goal, Integer assistTotal, BigDecimal shotsPerGame, BigDecimal keyPassPerGame, BigDecimal dribbleWonPerGame, BigDecimal foulGivenPerGame, BigDecimal offsideGivenPerGame, BigDecimal dispossessedPerGame, BigDecimal turnoverPerGame) {
    public MetricasOfensivas {
        shotsPerGame=escala(shotsPerGame); keyPassPerGame=escala(keyPassPerGame); dribbleWonPerGame=escala(dribbleWonPerGame); foulGivenPerGame=escala(foulGivenPerGame); offsideGivenPerGame=escala(offsideGivenPerGame); dispossessedPerGame=escala(dispossessedPerGame); turnoverPerGame=escala(turnoverPerGame);
        validar(goal,"goal"); validar(assistTotal,"assistTotal"); validar(shotsPerGame,"shotsPerGame"); validar(keyPassPerGame,"keyPassPerGame"); validar(dribbleWonPerGame,"dribbleWonPerGame"); validar(foulGivenPerGame,"foulGivenPerGame"); validar(offsideGivenPerGame,"offsideGivenPerGame"); validar(dispossessedPerGame,"dispossessedPerGame"); validar(turnoverPerGame,"turnoverPerGame");
    }
    private static BigDecimal escala(BigDecimal v) { return v == null ? null : v.setScale(2, java.math.RoundingMode.HALF_UP); }
    private static void validar(Number v, String n) { if (v instanceof BigDecimal d && d.signum()<0 || v instanceof Integer i && i<0) throw new IllegalArgumentException("La métrica "+n+" no puede ser negativa."); }
}
