package com.devchesterunited.devchesterunitedv2.modelo.estadisticas;
import java.math.BigDecimal;
public record MetricasPases(BigDecimal totalPassesPerGame, BigDecimal passSuccess, BigDecimal accurateCrossesPerGame,
        BigDecimal accurateLongPassPerGame, BigDecimal accurateThroughBallPerGame) {
    public MetricasPases {
        validar(totalPassesPerGame, "totalPassesPerGame");
        if (passSuccess != null && (passSuccess.signum() < 0
                || passSuccess.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new IllegalArgumentException("passSuccess debe estar entre 0 y 100.");
        }
        validar(accurateCrossesPerGame, "accurateCrossesPerGame");
        validar(accurateLongPassPerGame, "accurateLongPassPerGame");
        validar(accurateThroughBallPerGame, "accurateThroughBallPerGame");
    }
    private static void validar(BigDecimal v, String n) { if (v != null && v.signum()<0) throw new IllegalArgumentException("La métrica "+n+" no puede ser negativa."); }
}
