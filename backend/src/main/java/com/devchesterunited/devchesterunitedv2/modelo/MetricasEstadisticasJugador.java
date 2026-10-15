package com.devchesterunited.devchesterunitedv2.modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Métricas normalizadas de una familia estadística. Los null representan ausencia legítima. */
public record MetricasEstadisticasJugador(MetricasGenerales generales, MetricasOfensivas ofensivas,
        MetricasDefensivas defensivas, MetricasPases pases) {
    public MetricasEstadisticasJugador {
        generales = generales == null ? new MetricasGenerales(null,null,null,null,null,null,null,null) : generales;
        ofensivas = ofensivas == null ? new MetricasOfensivas(null,null,null,null,null,null,null,null,null) : ofensivas;
        defensivas = defensivas == null ? new MetricasDefensivas(null,null,null,null,null,null,null,null) : defensivas;
        pases = pases == null ? new MetricasPases(null,null,null,null,null) : pases;
    }

    /** Constructor compatible con el snapshot histórico de diez métricas. */
    public MetricasEstadisticasJugador(Integer minutos, Integer goles, Integer asistencias, BigDecimal tiros,
            BigDecimal pasesClave, BigDecimal regates, BigDecimal tackles, BigDecimal rating,
            Integer amarillas, Integer rojas) {
        this(new MetricasGenerales(null,null,minutos,null,null,decimal(rating),amarillas,rojas),
                new MetricasOfensivas(goles,asistencias,decimal(tiros),decimal(pasesClave),decimal(regates),null,null,null,null),
                new MetricasDefensivas(decimal(tackles),null,null,null,null,null,null,null),
                new MetricasPases(null,null,null,null,null));
    }

    public Integer minutosJugados() { return generales.minsPlayed(); }
    public Integer goles() { return ofensivas.goal(); }
    public Integer asistencias() { return ofensivas.assistTotal(); }
    public BigDecimal tiros() { return ofensivas.shotsPerGame(); }
    public BigDecimal pasesClave() { return ofensivas.keyPassPerGame(); }
    public BigDecimal regates() { return ofensivas.dribbleWonPerGame(); }
    public BigDecimal tackles() { return defensivas.tacklePerGame(); }
    public BigDecimal valoracion() { return generales.rating(); }
    public Integer tarjetasAmarillas() { return generales.yellowCard(); }
    public Integer tarjetasRojas() { return generales.redCard(); }
    private static BigDecimal decimal(BigDecimal value) { return value == null ? null : value.setScale(2, RoundingMode.HALF_UP); }

    private static BigDecimal normalizarDecimal(BigDecimal valor) {
        return valor == null ? null : valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static void validarNoNegativo(Number valor, String campo) {
        if (valor instanceof BigDecimal decimal && decimal.signum() < 0
                || valor instanceof Integer entero && entero < 0) {
            throw new IllegalArgumentException("La métrica " + campo + " no puede ser negativa.");
        }
    }
}
