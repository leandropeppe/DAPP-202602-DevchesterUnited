package com.devchesterunited.devchesterunitedv2.modelo.valorizacion;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EstrategiaGeneralV1 implements EstrategiaValorizacion {
    private static final Map<MetricaValorizacion, BigDecimal> PESOS = Map.of(
            MetricaValorizacion.GOALS, new BigDecimal("0.25"),
            MetricaValorizacion.ASSISTS, new BigDecimal("0.15"),
            MetricaValorizacion.SHOTS, new BigDecimal("0.10"),
            MetricaValorizacion.KEY_PASSES, new BigDecimal("0.10"),
            MetricaValorizacion.DRIBBLES, new BigDecimal("0.10"),
            MetricaValorizacion.TACKLES, new BigDecimal("0.10"),
            MetricaValorizacion.RATING, new BigDecimal("0.20"));

    private final BigDecimal valorBase;
    private final BigDecimal factorEscala;

    public EstrategiaGeneralV1(BigDecimal valorBase, BigDecimal factorEscala) {
        this.valorBase = valorBase;
        this.factorEscala = factorEscala;
    }

    public String id() { return "GENERAL"; }
    public int version() { return 1; }
    public TipoEstrategiaValorizacion tipo() { return TipoEstrategiaValorizacion.GENERAL; }
    public boolean esProvisoria() { return false; }
    public Map<MetricaValorizacion, BigDecimal> pesosPositivos(String posicion) { return PESOS; }
    public Map<MetricaValorizacion, BigDecimal> pesosPenalizacion(String posicion) { return Map.of(); }
    public BigDecimal valorBase() { return valorBase; }
    public BigDecimal factorEscala() { return factorEscala; }

    public ConfiguracionEstrategia configuracion() {
        return new ConfiguracionEstrategia(id(), version(), tipo(), esProvisoria(),
                new LinkedHashMap<>(PESOS), Map.of(), valorBase(), factorEscala());
    }
}
