package com.devchesterunited.devchesterunitedv2.modelo.valorizacion;

import java.math.BigDecimal;
import java.util.Map;

public interface EstrategiaValorizacion {
    String id();
    int version();
    TipoEstrategiaValorizacion tipo();
    boolean esProvisoria();
    default boolean esProvisoria(String posicion) { return false; }
    Map<MetricaValorizacion, BigDecimal> pesosPositivos(String posicion);
    Map<MetricaValorizacion, BigDecimal> pesosPenalizacion(String posicion);
    BigDecimal valorBase();
    BigDecimal factorEscala();

    /** Factor opcional de mercado; solo las estrategias posicionales lo utilizan. */
    default BigDecimal factorMercadoPosicion(String posicion) { return BigDecimal.ONE; }

    default String posicionNormalizadaParaFactor(String posicion) { return posicion; }

    default boolean soporta(String posicion) {
        return tipo() == TipoEstrategiaValorizacion.GENERAL
                || posicion != null && switch (posicion) {
                    case "Forward", "Midfielder", "Defender", "Goalkeeper", "DL", "MC", "DF", "PO" -> true;
                    default -> false;
                };
    }
}
