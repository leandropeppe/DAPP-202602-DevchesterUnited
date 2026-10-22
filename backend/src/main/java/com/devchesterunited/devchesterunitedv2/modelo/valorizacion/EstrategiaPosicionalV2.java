package com.devchesterunited.devchesterunitedv2.modelo.valorizacion;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Estrategia posicional basada en z-score por posición, sin rating ni factor de mercado. */
public final class EstrategiaPosicionalV2 implements EstrategiaValorizacion {
    public enum Bloque { PRODUCCION, CREACION, AMENAZA, IMPACTO, DISCIPLINA, DISTRIBUCION, TECNICO, AMENAZA_DEFENSIVA }

    static final BigDecimal PENDIENTE_SIGMOIDE = BigDecimal.valueOf(0.65);
    static final BigDecimal LIMITE_NORMALIZACION_MAN_OF_THE_MATCH = BigDecimal.valueOf(0.75);
    private static final String FORWARD = "Forward";
    private static final String MIDFIELDER = "Midfielder";
    private static final String DEFENDER = "Defender";
    private static final String GOALKEEPER = "Goalkeeper";
    private static final BigDecimal BASE = BigDecimal.ONE;
    private static final BigDecimal ESCALA = BigDecimal.valueOf(99);
    private static final BigDecimal CONFIABILIDAD_MINUTOS = BigDecimal.valueOf(300);
    private static final Map<MetricaValorizacion, BigDecimal> GOALS_ASSISTS = Map.of(MetricaValorizacion.GOALS, BigDecimal.ONE, MetricaValorizacion.ASSISTS, BigDecimal.ONE);
    private static final Map<MetricaValorizacion, BigDecimal> CREACION = Map.of(MetricaValorizacion.KEY_PASSES, BigDecimal.ONE, MetricaValorizacion.PASS_SUCCESS, BigDecimal.ONE, MetricaValorizacion.ACCURATE_CROSSES, BigDecimal.ONE, MetricaValorizacion.THROUGH_BALLS, BigDecimal.ONE);
    private static final Map<MetricaValorizacion, BigDecimal> AMENAZA = Map.of(MetricaValorizacion.SHOTS, BigDecimal.ONE, MetricaValorizacion.DRIBBLES, BigDecimal.ONE, MetricaValorizacion.AERIAL_WON, BigDecimal.ONE);
    private static final Map<MetricaValorizacion, BigDecimal> DISCIPLINA = Map.of(MetricaValorizacion.DISPOSSESSED, BigDecimal.ONE, MetricaValorizacion.TURNOVER, BigDecimal.ONE, MetricaValorizacion.YELLOW_CARDS, BigDecimal.ONE, MetricaValorizacion.RED_CARDS, BigDecimal.ONE);
    private static final Map<String, Map<Bloque, BigDecimal>> PESOS_BLOQUE = pesosBloque();
    private static final Map<String, Map<Bloque, Map<MetricaValorizacion, BigDecimal>>> METRICAS = metricas();

    @Override public String id() { return "POSITIONAL"; }
    @Override public int version() { return 2; }
    @Override public TipoEstrategiaValorizacion tipo() { return TipoEstrategiaValorizacion.POSICIONAL; }
    @Override public boolean esProvisoria() { return false; }
    @Override public BigDecimal valorBase() { return BASE; }
    @Override public BigDecimal factorEscala() { return ESCALA; }
    @Override public BigDecimal factorMercadoPosicion(String posicion) { return BigDecimal.ONE; }
    @Override public Map<MetricaValorizacion, BigDecimal> pesosPositivos(String posicion) {
        return aplanar(posicion, false);
    }
    @Override public Map<MetricaValorizacion, BigDecimal> pesosPenalizacion(String posicion) {
        return aplanar(posicion, true);
    }

    public Map<Bloque, BigDecimal> pesosBloque(String posicion) {
        return PESOS_BLOQUE.getOrDefault(normalizarPosicion(posicion), Map.of());
    }

    public Map<Bloque, Map<MetricaValorizacion, BigDecimal>> metricasPorBloque(String posicion) {
        return METRICAS.getOrDefault(normalizarPosicion(posicion), Map.of());
    }

    /** Penalización suave: minutos/(minutos+300), aplicada alrededor del score neutro 0.5. */
    public BigDecimal confiabilidad(Integer minutos) {
        if (minutos == null || minutos <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(minutos).divide(BigDecimal.valueOf(minutos)
                .add(CONFIABILIDAD_MINUTOS), 8, java.math.RoundingMode.HALF_UP);
    }

    BigDecimal pendienteSigmoide() {
        return PENDIENTE_SIGMOIDE;
    }

    BigDecimal limiteNormalizacionManOfTheMatch() {
        return LIMITE_NORMALIZACION_MAN_OF_THE_MATCH;
    }

    private Map<MetricaValorizacion, BigDecimal> aplanar(String posicion, boolean penalizaciones) {
        Map<MetricaValorizacion, BigDecimal> salida = new LinkedHashMap<>();
        String clave = normalizarPosicion(posicion);
        for (Map.Entry<Bloque, Map<MetricaValorizacion, BigDecimal>> bloque : METRICAS.getOrDefault(clave, Map.of()).entrySet()) {
            BigDecimal pesoBloque = PESOS_BLOQUE.get(clave).get(bloque.getKey());
            Map<MetricaValorizacion, BigDecimal> metricas = bloque.getValue();
            if (bloque.getKey() == Bloque.DISCIPLINA) {
                if (penalizaciones) metricas.forEach((metrica, peso) -> salida.put(metrica, pesoBloque.multiply(peso)));
            } else if (!penalizaciones) {
                metricas.forEach((metrica, peso) -> salida.put(metrica, pesoBloque.multiply(peso)));
            }
        }
        return salida;
    }

    public static String normalizarPosicion(String posicion) {
        if (posicion == null) return "";
        return switch (posicion) {
            case "DL" -> FORWARD; case "MC" -> MIDFIELDER; case "DF" -> DEFENDER; case "PO" -> GOALKEEPER;
            default -> posicion;
        };
    }

    private static Map<String, Map<Bloque, BigDecimal>> pesosBloque() {
        Map<String, Map<Bloque, BigDecimal>> m = new LinkedHashMap<>();
        m.put(FORWARD, pesos(Bloque.PRODUCCION, .30, Bloque.CREACION, .25, Bloque.AMENAZA, .20, Bloque.IMPACTO, .15, Bloque.DISCIPLINA, .10));
        m.put(MIDFIELDER, pesos(Bloque.PRODUCCION, .20, Bloque.CREACION, .25, Bloque.AMENAZA, .20, Bloque.IMPACTO, .25, Bloque.DISCIPLINA, .10));
        m.put(DEFENDER, pesos(Bloque.PRODUCCION, .10, Bloque.CREACION, .20, Bloque.AMENAZA_DEFENSIVA, .35, Bloque.IMPACTO, .25, Bloque.DISCIPLINA, .10));
        m.put(GOALKEEPER, pesos(Bloque.PRODUCCION, .10, Bloque.DISTRIBUCION, .35, Bloque.TECNICO, .20, Bloque.IMPACTO, .25, Bloque.DISCIPLINA, .10));
        return Map.copyOf(m);
    }

    private static Map<Bloque, BigDecimal> pesos(Object... datos) {
        Map<Bloque, BigDecimal> m = new EnumMap<>(Bloque.class);
        for (int i = 0; i < datos.length; i += 2) m.put((Bloque) datos[i], BigDecimal.valueOf((Double) datos[i + 1]));
        return Map.copyOf(m);
    }

    private static Map<String, Map<Bloque, Map<MetricaValorizacion, BigDecimal>>> metricas() {
        Map<String, Map<Bloque, Map<MetricaValorizacion, BigDecimal>>> m = new LinkedHashMap<>();
        m.put(FORWARD, bloques(
                Bloque.PRODUCCION, GOALS_ASSISTS, Bloque.CREACION, CREACION, Bloque.AMENAZA, AMENAZA,
                Bloque.IMPACTO, Map.of(MetricaValorizacion.MAN_OF_THE_MATCH, BigDecimal.ONE, MetricaValorizacion.FOUL_GIVEN, BigDecimal.ONE),
                Bloque.DISCIPLINA, DISCIPLINA));
        m.put(MIDFIELDER, bloques(
                Bloque.PRODUCCION, GOALS_ASSISTS, Bloque.CREACION, Map.of(MetricaValorizacion.KEY_PASSES, BigDecimal.ONE, MetricaValorizacion.PASS_SUCCESS, BigDecimal.ONE, MetricaValorizacion.TOTAL_PASSES, BigDecimal.ONE, MetricaValorizacion.LONG_PASSES, BigDecimal.ONE, MetricaValorizacion.THROUGH_BALLS, BigDecimal.ONE),
                Bloque.AMENAZA, Map.of(MetricaValorizacion.SHOTS, BigDecimal.ONE, MetricaValorizacion.DRIBBLES, BigDecimal.ONE),
                Bloque.IMPACTO, Map.of(MetricaValorizacion.TACKLES, BigDecimal.ONE, MetricaValorizacion.INTERCEPTIONS, BigDecimal.ONE, MetricaValorizacion.MAN_OF_THE_MATCH, BigDecimal.ONE), Bloque.DISCIPLINA, DISCIPLINA));
        m.put(DEFENDER, bloques(
                Bloque.PRODUCCION, GOALS_ASSISTS, Bloque.CREACION, Map.of(MetricaValorizacion.TOTAL_PASSES, BigDecimal.ONE, MetricaValorizacion.PASS_SUCCESS, BigDecimal.ONE, MetricaValorizacion.THROUGH_BALLS, BigDecimal.ONE),
                Bloque.AMENAZA_DEFENSIVA, Map.of(MetricaValorizacion.TACKLES, BigDecimal.ONE, MetricaValorizacion.INTERCEPTIONS, BigDecimal.ONE, MetricaValorizacion.CLEARANCES, BigDecimal.ONE, MetricaValorizacion.BLOCKS, BigDecimal.ONE, MetricaValorizacion.AERIAL_WON, BigDecimal.ONE, MetricaValorizacion.OFFSIDES_WON, BigDecimal.ONE),
                Bloque.IMPACTO, Map.of(MetricaValorizacion.MAN_OF_THE_MATCH, BigDecimal.ONE), Bloque.DISCIPLINA, Map.of(MetricaValorizacion.FOULS, BigDecimal.ONE, MetricaValorizacion.WAS_DRIBBLED, BigDecimal.ONE, MetricaValorizacion.GOAL_OWN, BigDecimal.ONE, MetricaValorizacion.YELLOW_CARDS, BigDecimal.ONE, MetricaValorizacion.RED_CARDS, BigDecimal.ONE)));
        m.put(GOALKEEPER, bloques(
                Bloque.PRODUCCION, GOALS_ASSISTS, Bloque.DISTRIBUCION, Map.of(MetricaValorizacion.PASS_SUCCESS, BigDecimal.ONE, MetricaValorizacion.TOTAL_PASSES, BigDecimal.ONE, MetricaValorizacion.LONG_PASSES, BigDecimal.ONE, MetricaValorizacion.THROUGH_BALLS, BigDecimal.ONE),
                Bloque.TECNICO, Map.of(MetricaValorizacion.AERIAL_WON, BigDecimal.ONE), Bloque.IMPACTO, Map.of(MetricaValorizacion.MAN_OF_THE_MATCH, BigDecimal.ONE),
                Bloque.DISCIPLINA, Map.of(MetricaValorizacion.FOULS, BigDecimal.ONE, MetricaValorizacion.YELLOW_CARDS, BigDecimal.ONE, MetricaValorizacion.RED_CARDS, BigDecimal.ONE)));
        return Map.copyOf(m);
    }

    @SuppressWarnings("unchecked")
    private static Map<Bloque, Map<MetricaValorizacion, BigDecimal>> bloques(Object... datos) {
        Map<Bloque, Map<MetricaValorizacion, BigDecimal>> m = new EnumMap<>(Bloque.class);
        for (int i = 0; i < datos.length; i += 2) m.put((Bloque) datos[i], (Map<MetricaValorizacion, BigDecimal>) datos[i + 1]);
        return Map.copyOf(m);
    }
}
