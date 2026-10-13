package com.devchesterunited.devchesterunitedv2.service.impl;

import com.devchesterunited.devchesterunitedv2.modelo.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.RegistroEstadisticasJugador;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Consolida familias sin convertir ausencias en ceros ni completar datos. */
@Component
public class ConsolidadorEstadisticasJugador {
    public MetricasEstadisticasJugador conservar(MetricasEstadisticasJugador anterior,
            MetricasEstadisticasJugador nueva) {
        return Acumulado.conservarInformacion(anterior, nueva);
    }

    public Resultado consolidar(Iterable<RegistroResuelto> registros) {
        Map<UUID, Acumulado> acumulados = new java.util.LinkedHashMap<>();
        for (RegistroResuelto registro : registros) {
            Acumulado acumulado = acumulados.computeIfAbsent(registro.jugadorId(), id -> new Acumulado());
            if (registro.registro().identidad().posicion() != null) {
                acumulado.posicion = registro.registro().identidad().posicion();
            }
            acumulado.aplicar(registro.registro().familia(), registro.registro().metricas());
        }
        return new Resultado(acumulados);
    }

    public record RegistroResuelto(UUID jugadorId, RegistroEstadisticasJugador registro) { }
    public record Resultado(Map<UUID, Acumulado> jugadores) { }

    public static final class Acumulado {
        private String posicion;
        private final Map<com.devchesterunited.devchesterunitedv2.modelo.FamiliaEstadistica,
                MetricasEstadisticasJugador> familias = new EnumMap<>(
                        com.devchesterunited.devchesterunitedv2.modelo.FamiliaEstadistica.class);
        void aplicar(com.devchesterunited.devchesterunitedv2.modelo.FamiliaEstadistica familia,
                MetricasEstadisticasJugador metricas) {
            familias.merge(familia, metricas, Acumulado::conservarInformacion);
        }

        public static MetricasEstadisticasJugador conservarInformacion(
                MetricasEstadisticasJugador anterior, MetricasEstadisticasJugador nueva) {
            return new MetricasEstadisticasJugador(
                    elegir(anterior.minutosJugados(), nueva.minutosJugados()),
                    elegir(anterior.goles(), nueva.goles()),
                    elegir(anterior.asistencias(), nueva.asistencias()),
                    elegir(anterior.tiros(), nueva.tiros()),
                    elegir(anterior.pasesClave(), nueva.pasesClave()),
                    elegir(anterior.regates(), nueva.regates()),
                    elegir(anterior.tackles(), nueva.tackles()),
                    elegir(anterior.valoracion(), nueva.valoracion()),
                    elegir(anterior.tarjetasAmarillas(), nueva.tarjetasAmarillas()),
                    elegir(anterior.tarjetasRojas(), nueva.tarjetasRojas()));
        }


        private static <T> T elegir(T anterior, T nueva) {
            return nueva != null ? nueva : anterior;
        }
        public String posicion() { return posicion; }
        public Map<com.devchesterunited.devchesterunitedv2.modelo.FamiliaEstadistica,
                MetricasEstadisticasJugador> familias() { return Map.copyOf(familias); }
    }
}
