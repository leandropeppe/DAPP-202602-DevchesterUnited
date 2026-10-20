package com.devchesterunited.devchesterunitedv2.modelo.valorizacion;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EstrategiaPosicionalV1 implements EstrategiaValorizacion {
    private final BigDecimal valorBase;
    private final BigDecimal factorEscala;
    private final Map<String, BigDecimal> factoresMercado;

    public EstrategiaPosicionalV1(BigDecimal valorBase, BigDecimal factorEscala) {
        this(valorBase, factorEscala, Map.of("DL", d("1.15"), "MC", d("1.05"),
                "DF", d("0.95"), "PO", d("0.90")));
    }

    public EstrategiaPosicionalV1(BigDecimal valorBase, BigDecimal factorEscala,
            Map<String, BigDecimal> factoresMercado) {
        this.valorBase = valorBase;
        this.factorEscala = factorEscala;
        if (factoresMercado == null || !factoresMercado.keySet().containsAll(java.util.Set.of("DL", "MC", "DF", "PO"))
                || factoresMercado.values().stream().anyMatch(v -> v == null || v.signum() < 0)) {
            throw new IllegalArgumentException("Los factores de mercado posicionales son inválidos.");
        }
        this.factoresMercado = Map.copyOf(new LinkedHashMap<>(factoresMercado));
    }
    private static final BigDecimal B = new BigDecimal("0.01");
    private static Map<MetricaValorizacion, BigDecimal> pesos(String posicion) {
        return switch (normalizar(posicion)) {
            case "DL" -> Map.of(GOALS(), d(".35"), ASSISTS(), d(".10"), SHOTS(), d(".18"), KEY_PASSES(), d(".12"), DRIBBLES(), d(".10"), RATING(), d(".15"));
            case "MC" -> Map.of(GOALS(), d(".10"), ASSISTS(), d(".15"), KEY_PASSES(), d(".20"), TOTAL_PASSES(), d(".15"), PASS_SUCCESS(), d(".14"), DRIBBLES(), d(".08"), TACKLES(), d(".08"), RATING(), d(".10"));
            case "DF" -> Map.of(GOALS(), d(".12"), TACKLES(), d(".20"), INTERCEPTIONS(), d(".19"), CLEARANCES(), d(".12"), BLOCKS(), d(".07"), TOTAL_PASSES(), d(".08"), PASS_SUCCESS(), d(".07"), RATING(), d(".15"));
            case "PO" -> Map.of(RATING(), d(".30"), PASS_SUCCESS(), d(".30"), LONG_PASSES(), d(".25"), MAN_OF_THE_MATCH(), d(".15"));
            default -> Map.of();
        };
    }

    private static Map<MetricaValorizacion, BigDecimal> penalizaciones(String posicion) {
        return switch (normalizar(posicion)) {
            case "DL" -> Map.of(FOULS(), d(".02"), YELLOW_CARDS(), d(".03"), RED_CARDS(), d(".10"), DISPOSSESSED(), d(".08"), OFFSIDES_GIVEN(), d(".05"), WAS_DRIBBLED(), BigDecimal.ZERO);
            case "MC" -> Map.of(FOULS(), d(".04"), YELLOW_CARDS(), d(".04"), RED_CARDS(), d(".10"), DISPOSSESSED(), d(".05"), WAS_DRIBBLED(), d(".03"), OFFSIDES_GIVEN(), d(".01"));
            case "DF" -> Map.of(FOULS(), d(".02"), YELLOW_CARDS(), d(".04"), RED_CARDS(), d(".10"), DISPOSSESSED(), d(".02"), WAS_DRIBBLED(), d(".08"), OFFSIDES_GIVEN(), BigDecimal.ZERO);
            default -> Map.of();
        };
    }

    public String id() { return "POSITIONAL"; }
    public int version() { return 1; }
    public TipoEstrategiaValorizacion tipo() { return TipoEstrategiaValorizacion.POSICIONAL; }
    public boolean esProvisoria() { return false; }
    public boolean esProvisoria(String posicion) { return "PO".equals(normalizar(posicion)); }
    public Map<MetricaValorizacion, BigDecimal> pesosPositivos(String posicion) { return pesos(posicion); }
    public Map<MetricaValorizacion, BigDecimal> pesosPenalizacion(String posicion) { return penalizaciones(posicion); }
    public BigDecimal valorBase() { return valorBase; }
    public BigDecimal factorEscala() { return factorEscala; }
    public BigDecimal factorMercadoPosicion(String posicion) { return factoresMercado.getOrDefault(normalizar(posicion), BigDecimal.ONE); }
    public String posicionNormalizadaParaFactor(String posicion) { return normalizar(posicion); }
    public Map<String, BigDecimal> factoresMercado() { return factoresMercado; }

    private static String normalizar(String posicion) {
        if (posicion == null) return "";
        return switch (posicion) { case "Forward" -> "DL"; case "Midfielder" -> "MC"; case "Defender" -> "DF"; case "Goalkeeper" -> "PO"; default -> posicion; };
    }
    private static BigDecimal d(String value) { return new BigDecimal(value); }
    private static MetricaValorizacion GOALS() { return MetricaValorizacion.GOALS; }
    private static MetricaValorizacion ASSISTS() { return MetricaValorizacion.ASSISTS; }
    private static MetricaValorizacion SHOTS() { return MetricaValorizacion.SHOTS; }
    private static MetricaValorizacion KEY_PASSES() { return MetricaValorizacion.KEY_PASSES; }
    private static MetricaValorizacion DRIBBLES() { return MetricaValorizacion.DRIBBLES; }
    private static MetricaValorizacion TACKLES() { return MetricaValorizacion.TACKLES; }
    private static MetricaValorizacion RATING() { return MetricaValorizacion.RATING; }
    private static MetricaValorizacion TOTAL_PASSES() { return MetricaValorizacion.TOTAL_PASSES; }
    private static MetricaValorizacion PASS_SUCCESS() { return MetricaValorizacion.PASS_SUCCESS; }
    private static MetricaValorizacion INTERCEPTIONS() { return MetricaValorizacion.INTERCEPTIONS; }
    private static MetricaValorizacion CLEARANCES() { return MetricaValorizacion.CLEARANCES; }
    private static MetricaValorizacion BLOCKS() { return MetricaValorizacion.BLOCKS; }
    private static MetricaValorizacion LONG_PASSES() { return MetricaValorizacion.LONG_PASSES; }
    private static MetricaValorizacion MAN_OF_THE_MATCH() { return MetricaValorizacion.MAN_OF_THE_MATCH; }
    private static MetricaValorizacion FOULS() { return MetricaValorizacion.FOULS; }
    private static MetricaValorizacion YELLOW_CARDS() { return MetricaValorizacion.YELLOW_CARDS; }
    private static MetricaValorizacion RED_CARDS() { return MetricaValorizacion.RED_CARDS; }
    private static MetricaValorizacion DISPOSSESSED() { return MetricaValorizacion.DISPOSSESSED; }
    private static MetricaValorizacion OFFSIDES_GIVEN() { return MetricaValorizacion.OFFSIDES_GIVEN; }
    private static MetricaValorizacion WAS_DRIBBLED() { return MetricaValorizacion.WAS_DRIBBLED; }
}
