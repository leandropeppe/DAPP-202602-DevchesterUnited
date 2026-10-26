package com.devchesterunited.devchesterunitedv2.modelo.estadisticas;
import java.math.BigDecimal;
public record MetricasGenerales(Integer apps, Integer subOn, Integer minsPlayed, Integer manOfTheMatch,
        BigDecimal aerialWonPerGame, BigDecimal rating, Integer yellowCard, Integer redCard) {
    public MetricasGenerales {
        aerialWonPerGame = escala(aerialWonPerGame); rating = escala(rating);
        validar(apps,"apps"); validar(subOn,"subOn"); validar(minsPlayed,"minsPlayed"); validar(manOfTheMatch,"manOfTheMatch");
        validar(aerialWonPerGame,"aerialWonPerGame"); validar(rating,"rating"); validar(yellowCard,"yellowCard"); validar(redCard,"redCard");
    }
    private static BigDecimal escala(BigDecimal v) { return v == null ? null : v.setScale(2, java.math.RoundingMode.HALF_UP); }
    private static void validar(Number v, String n) { if (v instanceof BigDecimal d && d.signum()<0 || v instanceof Integer i && i<0) throw new IllegalArgumentException("La métrica " + n + " no puede ser negativa."); }
}
