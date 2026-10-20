package com.devchesterunited.devchesterunitedv2.modelo.valorizacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class MotorValorizacion {
    public List<ResultadoValorizacion> calcular(EstrategiaValorizacion estrategia,
            List<JugadorEstadistico> jugadores, ConfiguracionElegibilidad elegibilidad) {
        Map<String, List<JugadorEstadistico>> poblaciones = poblaciones(estrategia, jugadores, elegibilidad);
        List<ResultadoValorizacion> resultados = new ArrayList<>();
        for (JugadorEstadistico jugador : jugadores) {
            ResultadoElegibilidad decision = evaluarElegibilidad(estrategia, jugador, elegibilidad);
            if (!decision.elegible()) {
                continue;
            }
            Map<MetricaValorizacion, Rango> rangos = rangos(estrategia,
                    poblaciones.getOrDefault(clavePoblacion(estrategia, jugador.posicion()), List.of()), jugador.posicion());
            Map<MetricaValorizacion, BigDecimal> positivos = estrategia.pesosPositivos(jugador.posicion());
            Map<MetricaValorizacion, BigDecimal> negativos = estrategia.pesosPenalizacion(jugador.posicion());
            BigDecimal score = BigDecimal.ZERO;
            BigDecimal penalizacion = BigDecimal.ZERO;
            List<ResultadoNormalizacion> trazas = new ArrayList<>();
            List<MetricaValorizacion> aplicadas = new ArrayList<>();
            for (Map.Entry<MetricaValorizacion, BigDecimal> entrada : positivos.entrySet()) {
                ResultadoNormalizacion normalizado = normalizar(entrada.getKey(), jugador, rangos,
                        estrategia, jugador.posicion());
                trazas.add(normalizado);
                score = score.add(normalizado.valorNormalizado().multiply(entrada.getValue()));
            }
            for (Map.Entry<MetricaValorizacion, BigDecimal> entrada : negativos.entrySet()) {
                if (entrada.getKey().extraer(jugador.metricas()) == null) {
                    continue;
                }
                ResultadoNormalizacion normalizado = normalizar(entrada.getKey(), jugador, rangos,
                        estrategia, jugador.posicion());
                trazas.add(normalizado);
                penalizacion = penalizacion.add(normalizado.valorNormalizado().multiply(entrada.getValue()));
                aplicadas.add(entrada.getKey());
            }
            BigDecimal scorePosicionalAntesAjuste = score.subtract(penalizacion).max(BigDecimal.ZERO).min(BigDecimal.ONE);
            BigDecimal factorMercado = estrategia.tipo() == TipoEstrategiaValorizacion.POSICIONAL
                    ? estrategia.factorMercadoPosicion(jugador.posicion()) : BigDecimal.ONE;
            String posicionNormalizada = estrategia.tipo() == TipoEstrategiaValorizacion.POSICIONAL
                    ? estrategia.posicionNormalizadaParaFactor(jugador.posicion()) : null;
            BigDecimal scoreFinal = scorePosicionalAntesAjuste.multiply(factorMercado)
                    .max(BigDecimal.ZERO).min(BigDecimal.ONE);
            resultados.add(new ResultadoValorizacion(jugador.jugadorId(), estrategia.id(), estrategia.version(),
                    scoreFinal, ConversorCotizacion.convertir(scoreFinal, estrategia.valorBase(), estrategia.factorEscala()),
                    trazas, aplicadas, decision, scorePosicionalAntesAjuste, factorMercado, posicionNormalizada));
        }
        return List.copyOf(resultados);
    }

    public ResultadoElegibilidad evaluarElegibilidad(EstrategiaValorizacion estrategia,
            JugadorEstadistico jugador, ConfiguracionElegibilidad configuracion) {
        List<String> causas = new ArrayList<>();
        if (!estrategia.soporta(jugador.posicion())) {
            causas.add("POSICION_NO_SOPORTADA");
        }
        Integer partidos = jugador.metricas().generales().apps();
        Integer minutos = jugador.metricas().generales().minsPlayed();
        if (configuracion.minimoPartidos() != null
                && (partidos == null || partidos < configuracion.minimoPartidos())) {
            causas.add("MUESTRA_PARTIDOS_INSUFICIENTE");
        }
        if (configuracion.minimoMinutos() != null
                && (minutos == null || minutos < configuracion.minimoMinutos())) {
            causas.add("MUESTRA_MINUTOS_INSUFICIENTE");
        }
        for (MetricaValorizacion metrica : estrategia.pesosPositivos(jugador.posicion()).keySet()) {
            if (metrica.extraer(jugador.metricas()) == null) {
                causas.add("METRICA_POSITIVA_AUSENTE:" + metrica.name());
            }
        }
        List<MetricaValorizacion> negativasAusentes = estrategia.pesosPenalizacion(jugador.posicion()).keySet().stream()
                .filter(metrica -> metrica.extraer(jugador.metricas()) == null).toList();
        return new ResultadoElegibilidad(jugador.jugadorId(), estrategia.id(), estrategia.version(),
                causas.isEmpty(), jugador.posicion(), causas, negativasAusentes);
    }

    private Map<String, List<JugadorEstadistico>> poblaciones(EstrategiaValorizacion estrategia,
            List<JugadorEstadistico> jugadores, ConfiguracionElegibilidad elegibilidad) {
        Map<String, List<JugadorEstadistico>> poblaciones = new LinkedHashMap<>();
        for (JugadorEstadistico jugador : jugadores) {
            if (estrategia.soporta(jugador.posicion())) {
                ResultadoElegibilidad decision = evaluarElegibilidad(estrategia, jugador, elegibilidad);
                if (decision.elegible()) {
                    poblaciones.computeIfAbsent(clavePoblacion(estrategia, jugador.posicion()), key -> new ArrayList<>())
                            .add(jugador);
                }
            }
        }
        return poblaciones;
    }

    private static Map<MetricaValorizacion, Rango> rangos(EstrategiaValorizacion estrategia,
            List<JugadorEstadistico> poblacion, String posicion) {
        Map<MetricaValorizacion, Rango> rangos = new HashMap<>();
        estrategia.pesosPositivos(posicion).keySet().forEach(metrica -> rangos.put(metrica, rango(metrica, poblacion)));
        estrategia.pesosPenalizacion(posicion).keySet().forEach(metrica -> rangos.putIfAbsent(metrica, rango(metrica, poblacion)));
        return rangos;
    }

    private static Rango rango(MetricaValorizacion metrica, List<JugadorEstadistico> jugadores) {
        List<BigDecimal> valores = jugadores.stream().map(j -> metrica.extraer(j.metricas()))
                .filter(java.util.Objects::nonNull).toList();
        if (valores.isEmpty()) return new Rango(null, null);
        BigDecimal min = valores.stream().min(BigDecimal::compareTo).orElseThrow();
        BigDecimal max = valores.stream().max(BigDecimal::compareTo).orElseThrow();
        return new Rango(min, max);
    }

    private static ResultadoNormalizacion normalizar(MetricaValorizacion metrica, JugadorEstadistico jugador,
            Map<MetricaValorizacion, Rango> rangos, EstrategiaValorizacion estrategia, String posicion) {
        BigDecimal valor = metrica.extraer(jugador.metricas());
        Rango rango = rangos.get(metrica);
        if (valor == null || rango.minimo() == null) {
            return new ResultadoNormalizacion(metrica, valor, rango == null ? null : rango.minimo(),
                    rango == null ? null : rango.maximo(), BigDecimal.ZERO, alcance(estrategia), posicion,
                    ResultadoNormalizacion.Estado.AUSENTE);
        }
        if (rango.minimo().compareTo(rango.maximo()) == 0) {
            return new ResultadoNormalizacion(metrica, valor, rango.minimo(), rango.maximo(), BigDecimal.ZERO,
                    alcance(estrategia), posicion, ResultadoNormalizacion.Estado.CONSTANTE);
        }
        BigDecimal normalizado = valor.subtract(rango.minimo()).divide(
                rango.maximo().subtract(rango.minimo()), 8, RoundingMode.HALF_UP);
        return new ResultadoNormalizacion(metrica, valor, rango.minimo(), rango.maximo(), normalizado,
                alcance(estrategia), posicion, ResultadoNormalizacion.Estado.CALCULADO);
    }

    private static String alcance(EstrategiaValorizacion estrategia) {
        return estrategia.tipo() == TipoEstrategiaValorizacion.GENERAL ? "GLOBAL" : "POSICION";
    }

    private static String clavePoblacion(EstrategiaValorizacion estrategia, String posicion) {
        return estrategia.tipo() == TipoEstrategiaValorizacion.GENERAL ? "GLOBAL" : posicion;
    }

    private record Rango(BigDecimal minimo, BigDecimal maximo) { }
}
