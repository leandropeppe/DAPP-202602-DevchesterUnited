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
            case "DL" -> Map.of(goals(), d(".35"), assists(), d(".10"), shots(), d(".18"), keyPasses(), d(".12"), dribbles(), d(".10"), rating(), d(".15"));
            case "MC" -> Map.of(goals(), d(".10"), assists(), d(".15"), keyPasses(), d(".20"), totalPasses(), d(".15"), passSuccess(), d(".14"), dribbles(), d(".08"), tackles(), d(".08"), rating(), d(".10"));
            case "DF" -> Map.of(goals(), d(".12"), tackles(), d(".20"), interceptions(), d(".19"), clearances(), d(".12"), blocks(), d(".07"), totalPasses(), d(".08"), passSuccess(), d(".07"), rating(), d(".15"));
            case "PO" -> Map.of(rating(), d(".30"), passSuccess(), d(".30"), longPasses(), d(".25"), manOfTheMatch(), d(".15"));
            default -> Map.of();
        };
    }

    private static Map<MetricaValorizacion, BigDecimal> penalizaciones(String posicion) {
        return switch (normalizar(posicion)) {
            case "DL" -> Map.of(fouls(), d(".02"), yellowCards(), d(".03"), redCards(), d(".10"), dispossessed(), d(".08"), offsidesGiven(), d(".05"), wasDribbled(), BigDecimal.ZERO);
            case "MC" -> Map.of(fouls(), d(".04"), yellowCards(), d(".04"), redCards(), d(".10"), dispossessed(), d(".05"), wasDribbled(), d(".03"), offsidesGiven(), d(".01"));
            case "DF" -> Map.of(fouls(), d(".02"), yellowCards(), d(".04"), redCards(), d(".10"), dispossessed(), d(".02"), wasDribbled(), d(".08"), offsidesGiven(), BigDecimal.ZERO);
            default -> Map.of();
        };
    }

    @Override
    public String id() { return "POSITIONAL"; }
    @Override
    public int version() { return 1; }
    @Override
    public TipoEstrategiaValorizacion tipo() { return TipoEstrategiaValorizacion.POSICIONAL; }
    @Override
    public boolean esProvisoria() { return false; }
    @Override
    public boolean esProvisoria(String posicion) { return "PO".equals(normalizar(posicion)); }
    @Override
    public Map<MetricaValorizacion, BigDecimal> pesosPositivos(String posicion) { return pesos(posicion); }
    @Override
    public Map<MetricaValorizacion, BigDecimal> pesosPenalizacion(String posicion) { return penalizaciones(posicion); }
    @Override
    public BigDecimal valorBase() { return valorBase; }
    @Override
    public BigDecimal factorEscala() { return factorEscala; }
    @Override
    public BigDecimal factorMercadoPosicion(String posicion) { return factoresMercado.getOrDefault(normalizar(posicion), BigDecimal.ONE); }
    @Override
    public String posicionNormalizadaParaFactor(String posicion) { return normalizar(posicion); }
    public Map<String, BigDecimal> factoresMercado() { return factoresMercado; }

    private static String normalizar(String posicion) {
        if (posicion == null) return "";
        return switch (posicion) { case "Forward" -> "DL"; case "Midfielder" -> "MC"; case "Defender" -> "DF"; case "Goalkeeper" -> "PO"; default -> posicion; };
    }
    private static BigDecimal d(String value) { return new BigDecimal(value); }
    private static MetricaValorizacion goals() { return MetricaValorizacion.GOALS; }
    private static MetricaValorizacion assists() { return MetricaValorizacion.ASSISTS; }
    private static MetricaValorizacion shots() { return MetricaValorizacion.SHOTS; }
    private static MetricaValorizacion keyPasses() { return MetricaValorizacion.KEY_PASSES; }
    private static MetricaValorizacion dribbles() { return MetricaValorizacion.DRIBBLES; }
    private static MetricaValorizacion tackles() { return MetricaValorizacion.TACKLES; }
    private static MetricaValorizacion rating() { return MetricaValorizacion.RATING; }
    private static MetricaValorizacion totalPasses() { return MetricaValorizacion.TOTAL_PASSES; }
    private static MetricaValorizacion passSuccess() { return MetricaValorizacion.PASS_SUCCESS; }
    private static MetricaValorizacion interceptions() { return MetricaValorizacion.INTERCEPTIONS; }
    private static MetricaValorizacion clearances() { return MetricaValorizacion.CLEARANCES; }
    private static MetricaValorizacion blocks() { return MetricaValorizacion.BLOCKS; }
    private static MetricaValorizacion longPasses() { return MetricaValorizacion.LONG_PASSES; }
    private static MetricaValorizacion manOfTheMatch() { return MetricaValorizacion.MAN_OF_THE_MATCH; }
    private static MetricaValorizacion fouls() { return MetricaValorizacion.FOULS; }
    private static MetricaValorizacion yellowCards() { return MetricaValorizacion.YELLOW_CARDS; }
    private static MetricaValorizacion redCards() { return MetricaValorizacion.RED_CARDS; }
    private static MetricaValorizacion dispossessed() { return MetricaValorizacion.DISPOSSESSED; }
    private static MetricaValorizacion offsidesGiven() { return MetricaValorizacion.OFFSIDES_GIVEN; }
    private static MetricaValorizacion wasDribbled() { return MetricaValorizacion.WAS_DRIBBLED; }
}
