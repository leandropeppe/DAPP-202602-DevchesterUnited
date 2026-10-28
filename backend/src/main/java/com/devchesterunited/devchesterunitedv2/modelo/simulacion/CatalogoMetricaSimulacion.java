package com.devchesterunited.devchesterunitedv2.modelo.simulacion;

import com.devchesterunited.devchesterunitedv2.modelo.valorizacion.MetricaValorizacion;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class CatalogoMetricaSimulacion {
    public enum Semantica {
        ACUMULATIVA,
        POR_PARTIDO,
        PORCENTAJE,
        RATING
    }

    public record Definicion(Semantica semantica, BigDecimal minimo, BigDecimal maximo, String etiqueta) {
        public Definicion {
            if (semantica == null || minimo == null || etiqueta == null || etiqueta.isBlank()
                    || maximo != null && minimo.compareTo(maximo) > 0) {
                throw new IllegalArgumentException("La definición de métrica es inválida.");
            }
        }

        public boolean acepta(BigDecimal valor) {
            return valor != null && valor.compareTo(minimo) >= 0
                    && (maximo == null || valor.compareTo(maximo) <= 0);
        }
    }

    private static final BigDecimal CERO = BigDecimal.ZERO;
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private static final BigDecimal DIEZ = BigDecimal.TEN;
    private static final Map<MetricaValorizacion, Definicion> DEFINICIONES = definiciones();

    private CatalogoMetricaSimulacion() {
    }

    public static Definicion definir(MetricaValorizacion metrica) {
        if (metrica == null || !DEFINICIONES.containsKey(metrica)) {
            throw new MetricaNoEditableException(String.valueOf(metrica));
        }
        return DEFINICIONES.get(metrica);
    }

    public static Set<MetricaValorizacion> metricas() {
        return DEFINICIONES.keySet();
    }

    public static boolean esAcumulativa(MetricaValorizacion metrica) {
        return definir(metrica).semantica() == Semantica.ACUMULATIVA;
    }

    public static boolean esPorPartido(MetricaValorizacion metrica) {
        return definir(metrica).semantica() == Semantica.POR_PARTIDO;
    }

    private static Map<MetricaValorizacion, Definicion> definiciones() {
        Map<MetricaValorizacion, Definicion> resultado = new java.util.EnumMap<>(MetricaValorizacion.class);
        resultado.put(MetricaValorizacion.GOALS, acumulativa("Goles"));
        resultado.put(MetricaValorizacion.ASSISTS, acumulativa("Asistencias"));
        resultado.put(MetricaValorizacion.MAN_OF_THE_MATCH, acumulativa("Jugador del partido"));
        resultado.put(MetricaValorizacion.YELLOW_CARDS, acumulativa("Tarjetas amarillas"));
        resultado.put(MetricaValorizacion.RED_CARDS, acumulativa("Tarjetas rojas"));
        resultado.put(MetricaValorizacion.GOAL_OWN, acumulativa("Goles en contra"));
        for (MetricaValorizacion metrica : MetricaValorizacion.values()) {
            resultado.putIfAbsent(metrica, porPartido(etiqueta(metrica)));
        }
        resultado.put(MetricaValorizacion.PASS_SUCCESS,
                new Definicion(Semantica.PORCENTAJE, CERO, CIEN, "Precisión de pase"));
        resultado.put(MetricaValorizacion.RATING,
                new Definicion(Semantica.RATING, CERO, DIEZ, "Valoración"));
        return Map.copyOf(resultado);
    }

    private static Definicion acumulativa(String etiqueta) {
        return new Definicion(Semantica.ACUMULATIVA, CERO, null, etiqueta);
    }

    private static Definicion porPartido(String etiqueta) {
        return new Definicion(Semantica.POR_PARTIDO, CERO, null, etiqueta);
    }

    private static String etiqueta(MetricaValorizacion metrica) {
        return java.util.Arrays.stream(metrica.name().split("_"))
                .map(String::toLowerCase).collect(Collectors.joining(" "));
    }
}
