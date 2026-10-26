package com.devchesterunited.devchesterunitedv2.modelo.valorizacion;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Component
public final class MotorValorizacion {
    private static final String CAMPO_POSICION = "POSICION";
    private static final String METODO_V2 = "Z_SCORE_SIGMOIDE_K_0_65";

    public List<ResultadoValorizacion> calcular(EstrategiaValorizacion estrategia,
            List<JugadorEstadistico> jugadores, ConfiguracionElegibilidad elegibilidad) {
        if (estrategia instanceof EstrategiaPosicionalV2 v2) {
            return calcularV2(v2, jugadores, elegibilidad);
        }
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
        if (estrategia instanceof EstrategiaPosicionalV2) {
            return evaluarElegibilidadV2(estrategia, jugador, configuracion);
        }
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
        estrategia.pesosPositivos(jugador.posicion()).keySet().stream()
                .filter(metrica -> metrica.extraer(jugador.metricas()) == null)
                .map(metrica -> "METRICA_POSITIVA_AUSENTE:" + metrica.name())
                .forEach(causas::add);
        List<MetricaValorizacion> negativasAusentes = estrategia.pesosPenalizacion(jugador.posicion()).keySet().stream()
                .filter(metrica -> metrica.extraer(jugador.metricas()) == null).toList();
        return new ResultadoElegibilidad(jugador.jugadorId(), estrategia.id(), estrategia.version(),
                causas.isEmpty(), jugador.posicion(), causas, negativasAusentes);
    }

    private List<ResultadoValorizacion> calcularV2(EstrategiaPosicionalV2 estrategia,
            List<JugadorEstadistico> jugadores, ConfiguracionElegibilidad elegibilidad) {
        Map<String, List<JugadorEstadistico>> poblaciones = new LinkedHashMap<>();
        for (JugadorEstadistico jugador : jugadores) {
            if (evaluarElegibilidadV2(estrategia, jugador, elegibilidad).elegible()) {
                poblaciones.computeIfAbsent(EstrategiaPosicionalV2.normalizarPosicion(jugador.posicion()), key -> new ArrayList<>()).add(jugador);
            }
        }
        List<ResultadoValorizacion> resultados = new ArrayList<>();
        for (JugadorEstadistico jugador : jugadores) {
            ResultadoElegibilidad decision = evaluarElegibilidadV2(estrategia, jugador, elegibilidad);
            if (!decision.elegible()) continue;
            String posicion = EstrategiaPosicionalV2.normalizarPosicion(jugador.posicion());
            Map<MetricaValorizacion, Distribucion> distribuciones = distribuciones(estrategia, poblaciones.getOrDefault(posicion, List.of()), posicion);
            List<ResultadoNormalizacion> trazas = new ArrayList<>();
            List<MetricaValorizacion> penalizaciones = new ArrayList<>();
            Map<EstrategiaPosicionalV2.Bloque, BigDecimal> pesosBloque = estrategia.pesosBloque(posicion);
            BigDecimal score = BigDecimal.ZERO;
            for (Map.Entry<EstrategiaPosicionalV2.Bloque, BigDecimal> bloque : pesosBloque.entrySet()) {
                Map<MetricaValorizacion, BigDecimal> metricas = estrategia.metricasPorBloque(posicion).getOrDefault(bloque.getKey(), Map.of());
                BigDecimal scoreBloque;
                if (bloque.getKey() == EstrategiaPosicionalV2.Bloque.AMENAZA_DEFENSIVA) {
                    scoreBloque = scoreAmenazaDefensiva(estrategia, jugador, distribuciones, trazas, posicion);
                } else {
                    scoreBloque = scoreBloqueV2(estrategia, jugador, bloque.getKey(), metricas,
                            distribuciones, trazas, penalizaciones, posicion);
                }
                score = score.add(scoreBloque.multiply(bloque.getValue()));
            }
            BigDecimal antes = score.max(BigDecimal.ZERO).min(BigDecimal.ONE);
            BigDecimal confiabilidad = estrategia.confiabilidad(jugador.metricas().generales().minsPlayed());
            BigDecimal scoreFinal = BigDecimal.valueOf(.5).add(confiabilidad.multiply(antes.subtract(BigDecimal.valueOf(.5))))
                    .max(BigDecimal.ZERO).min(BigDecimal.ONE);
            resultados.add(new ResultadoValorizacion(jugador.jugadorId(), estrategia.id(), estrategia.version(), scoreFinal,
                    ConversorCotizacion.convertir(scoreFinal, estrategia.valorBase(), estrategia.factorEscala()), trazas,
                    penalizaciones, decision, antes, BigDecimal.ONE, null));
        }
        return List.copyOf(resultados);
    }

    private ResultadoElegibilidad evaluarElegibilidadV2(EstrategiaValorizacion estrategia, JugadorEstadistico jugador,
            ConfiguracionElegibilidad configuracion) {
        List<String> causas = new ArrayList<>();
        if (!estrategia.soporta(jugador.posicion())) causas.add("POSICION_NO_SOPORTADA");
        Integer partidos = jugador.metricas().generales().apps();
        Integer minutos = jugador.metricas().generales().minsPlayed();
        if (configuracion.minimoPartidos() != null && (partidos == null || partidos < configuracion.minimoPartidos())) causas.add("MUESTRA_PARTIDOS_INSUFICIENTE");
        if (configuracion.minimoMinutos() != null && (minutos == null || minutos < configuracion.minimoMinutos())) causas.add("MUESTRA_MINUTOS_INSUFICIENTE");
        List<MetricaValorizacion> ausentes = estrategia.pesosPenalizacion(jugador.posicion()).keySet().stream()
                .filter(metrica -> valorV2(metrica, jugador) == null).toList();
        return new ResultadoElegibilidad(jugador.jugadorId(), estrategia.id(), estrategia.version(), causas.isEmpty(), jugador.posicion(), causas, ausentes);
    }

    private static Map<MetricaValorizacion, Distribucion> distribuciones(EstrategiaPosicionalV2 estrategia,
            List<JugadorEstadistico> jugadores, String posicion) {
        Map<MetricaValorizacion, Distribucion> salida = new EnumMap<>(MetricaValorizacion.class);
        estrategia.metricasPorBloque(posicion).values().stream().flatMap(m -> m.keySet().stream()).distinct().forEach(metrica -> {
            List<BigDecimal> valores = jugadores.stream().map(j -> valorV2(metrica, j)).filter(java.util.Objects::nonNull).toList();
            if (!valores.isEmpty()) {
                double media = valores.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
                double varianza = valores.stream().mapToDouble(v -> Math.pow(v.doubleValue() - media, 2)).average().orElse(0);
                salida.put(metrica, new Distribucion(BigDecimal.valueOf(media), BigDecimal.valueOf(Math.sqrt(varianza))));
            }
        });
        return salida;
    }

    private static BigDecimal valorV2(MetricaValorizacion metrica, JugadorEstadistico jugador) {
        BigDecimal valor = metrica.extraer(jugador.metricas());
        if (metrica != MetricaValorizacion.GOALS && metrica != MetricaValorizacion.ASSISTS) return valor;
        Integer minutos = jugador.metricas().generales().minsPlayed();
        if (valor == null || minutos == null || minutos <= 0) return null;
        return valor.multiply(BigDecimal.valueOf(90)).divide(BigDecimal.valueOf(minutos), 8, RoundingMode.HALF_UP);
    }

    private static BigDecimal scoreBloqueV2(EstrategiaPosicionalV2 estrategia, JugadorEstadistico jugador,
            EstrategiaPosicionalV2.Bloque bloque, Map<MetricaValorizacion, BigDecimal> metricas,
            Map<MetricaValorizacion, Distribucion> distribuciones, List<ResultadoNormalizacion> trazas,
            List<MetricaValorizacion> penalizaciones, String posicion) {
        BigDecimal suma = BigDecimal.ZERO;
        int validas = 0;
        for (MetricaValorizacion metrica : metricas.keySet()) {
            BigDecimal valor = valorV2(metrica, jugador);
            Distribucion distribucion = distribuciones.get(metrica);
            if (valor == null || distribucion == null) {
                trazas.add(trazaV2(metrica, valor, distribucion, BigDecimal.valueOf(.5), posicion,
                        ResultadoNormalizacion.Estado.AUSENTE, METODO_V2));
                continue;
            }
            BigDecimal normalizado = normalizadoV2(estrategia, metrica, valor, distribucion);
            boolean inversa = bloque == EstrategiaPosicionalV2.Bloque.DISCIPLINA;
            if (inversa) {
                normalizado = BigDecimal.ONE.subtract(normalizado);
                penalizaciones.add(metrica);
            }
            trazas.add(trazaV2(metrica, valor, distribucion, normalizado, posicion,
                    distribucion.desviacion().signum() == 0 ? ResultadoNormalizacion.Estado.CONSTANTE
                            : ResultadoNormalizacion.Estado.CALCULADO,
                    METODO_V2));
            suma = suma.add(normalizado);
            validas++;
        }
        return validas == 0 ? BigDecimal.valueOf(.5)
                : suma.divide(BigDecimal.valueOf(validas), 8, RoundingMode.HALF_UP);
    }

    /**
     * Amenaza defensiva: cuatro subfamilias independientes para no contar varias veces
     * la misma dimensión de actividad defensiva. Las métricas ausentes se omiten dentro
     * de su subfamilia y las subfamilias vacías se omiten del promedio final.
     */
    private static BigDecimal scoreAmenazaDefensiva(EstrategiaPosicionalV2 estrategia, JugadorEstadistico jugador,
            Map<MetricaValorizacion, Distribucion> distribuciones, List<ResultadoNormalizacion> trazas,
            String posicion) {
        List<List<MetricaValorizacion>> subfamilias = List.of(
                List.of(MetricaValorizacion.TACKLES, MetricaValorizacion.INTERCEPTIONS, MetricaValorizacion.BLOCKS),
                List.of(MetricaValorizacion.CLEARANCES),
                List.of(MetricaValorizacion.AERIAL_WON),
                List.of(MetricaValorizacion.OFFSIDES_WON));
        BigDecimal sumaSubfamilias = BigDecimal.ZERO;
        int subfamiliasValidas = 0;
        for (List<MetricaValorizacion> subfamilia : subfamilias) {
            BigDecimal suma = BigDecimal.ZERO;
            int validas = 0;
            for (MetricaValorizacion metrica : subfamilia) {
                BigDecimal valor = valorV2(metrica, jugador);
                Distribucion distribucion = distribuciones.get(metrica);
                if (valor == null || distribucion == null) {
                    trazas.add(trazaV2(metrica, valor, distribucion, BigDecimal.valueOf(.5), posicion,
                            ResultadoNormalizacion.Estado.AUSENTE, metodoSubfamilia(metrica)));
                    continue;
                }
                BigDecimal normalizado = normalizadoV2(estrategia, metrica, valor, distribucion);
                trazas.add(trazaV2(metrica, valor, distribucion, normalizado, posicion,
                        distribucion.desviacion().signum() == 0 ? ResultadoNormalizacion.Estado.CONSTANTE
                                : ResultadoNormalizacion.Estado.CALCULADO,
                        metodoSubfamilia(metrica)));
                suma = suma.add(normalizado);
                validas++;
            }
            if (validas > 0) {
                sumaSubfamilias = sumaSubfamilias.add(suma.divide(BigDecimal.valueOf(validas), 8, RoundingMode.HALF_UP));
                subfamiliasValidas++;
            }
        }
        return subfamiliasValidas == 0 ? BigDecimal.valueOf(.5)
                : sumaSubfamilias.divide(BigDecimal.valueOf(subfamiliasValidas), 8, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalizadoV2(EstrategiaPosicionalV2 estrategia, MetricaValorizacion metrica,
            BigDecimal valor, Distribucion distribucion) {
        if (distribucion.desviacion().signum() == 0) {
            return BigDecimal.valueOf(.5);
        }
        double z = (valor.doubleValue() - distribucion.media().doubleValue())
                / distribucion.desviacion().doubleValue();
        BigDecimal normalizado = BigDecimal.valueOf(1.0 / (1.0
                + Math.exp(-estrategia.pendienteSigmoide().doubleValue() * z)));
        if (metrica == MetricaValorizacion.MAN_OF_THE_MATCH) {
            normalizado = normalizado.min(estrategia.limiteNormalizacionManOfTheMatch());
        }
        return normalizado;
    }

    private static String metodoSubfamilia(MetricaValorizacion metrica) {
        if (metrica == MetricaValorizacion.TACKLES || metrica == MetricaValorizacion.INTERCEPTIONS
                || metrica == MetricaValorizacion.BLOCKS) {
            return "Z_SCORE_SIGMOIDE_K_0_65_AMENAZA_DEFENSIVA_CONTENCION";
        }
        if (metrica == MetricaValorizacion.CLEARANCES) {
            return "Z_SCORE_SIGMOIDE_K_0_65_AMENAZA_DEFENSIVA_DESPEJES";
        }
        if (metrica == MetricaValorizacion.AERIAL_WON) {
            return "Z_SCORE_SIGMOIDE_K_0_65_AMENAZA_DEFENSIVA_AEREO";
        }
        return "Z_SCORE_SIGMOIDE_K_0_65_AMENAZA_DEFENSIVA_LECTURA_LINEA";
    }

    private static ResultadoNormalizacion trazaV2(MetricaValorizacion metrica, BigDecimal valor, Distribucion d,
            BigDecimal normalizado, String posicion, ResultadoNormalizacion.Estado estado, String metodo) {
        return new ResultadoNormalizacion(metrica, valor, d == null ? null : d.media(), d == null ? null : d.desviacion(),
                normalizado, CAMPO_POSICION, posicion, estado, metodo);
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
        Map<MetricaValorizacion, Rango> rangos = new EnumMap<>(MetricaValorizacion.class);
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
        return estrategia.tipo() == TipoEstrategiaValorizacion.GENERAL ? "GLOBAL" : CAMPO_POSICION;
    }

    private static String clavePoblacion(EstrategiaValorizacion estrategia, String posicion) {
        return estrategia.tipo() == TipoEstrategiaValorizacion.GENERAL ? "GLOBAL" : posicion;
    }

    private record Rango(BigDecimal minimo, BigDecimal maximo) { }
    private record Distribucion(BigDecimal media, BigDecimal desviacion) { }
}
