package com.devchesterunited.devchesterunitedv2.service.impl.estadisticas;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.RegistroEstadisticasJugador;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

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
        private final Map<com.devchesterunited.devchesterunitedv2.modelo.estadisticas.FamiliaEstadistica,
                MetricasEstadisticasJugador> familias = new EnumMap<>(
                        com.devchesterunited.devchesterunitedv2.modelo.estadisticas.FamiliaEstadistica.class);
        void aplicar(com.devchesterunited.devchesterunitedv2.modelo.estadisticas.FamiliaEstadistica familia,
                MetricasEstadisticasJugador metricas) {
            familias.merge(familia, metricas, Acumulado::conservarInformacion);
        }

    public static MetricasEstadisticasJugador conservarInformacion(
            MetricasEstadisticasJugador anterior, MetricasEstadisticasJugador nueva) {
        return new MetricasEstadisticasJugador(
                    generales(anterior.generales(), nueva.generales()),
                    ofensivas(anterior.ofensivas(), nueva.ofensivas()),
                    defensivas(anterior.defensivas(), nueva.defensivas()),
                    pases(anterior.pases(), nueva.pases()));
        }

        private static com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasGenerales generales(
                com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasGenerales a,
                com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasGenerales n) {
            return new com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasGenerales(
                    elegir(a.apps(), n.apps()), elegir(a.subOn(), n.subOn()), elegir(a.minsPlayed(), n.minsPlayed()),
                    elegir(a.manOfTheMatch(), n.manOfTheMatch()), elegir(a.aerialWonPerGame(), n.aerialWonPerGame()),
                    elegir(a.rating(), n.rating()), elegir(a.yellowCard(), n.yellowCard()), elegir(a.redCard(), n.redCard()));
        }

        private static com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasOfensivas ofensivas(
                com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasOfensivas a,
                com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasOfensivas n) {
            return new com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasOfensivas(
                    elegir(a.goal(), n.goal()), elegir(a.assistTotal(), n.assistTotal()),
                    elegir(a.shotsPerGame(), n.shotsPerGame()), elegir(a.keyPassPerGame(), n.keyPassPerGame()),
                    elegir(a.dribbleWonPerGame(), n.dribbleWonPerGame()), elegir(a.foulGivenPerGame(), n.foulGivenPerGame()),
                    elegir(a.offsideGivenPerGame(), n.offsideGivenPerGame()), elegir(a.dispossessedPerGame(), n.dispossessedPerGame()),
                    elegir(a.turnoverPerGame(), n.turnoverPerGame()));
        }

        private static com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasDefensivas defensivas(
                com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasDefensivas a,
                com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasDefensivas n) {
            return new com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasDefensivas(
                    elegir(a.tacklePerGame(), n.tacklePerGame()), elegir(a.interceptionPerGame(), n.interceptionPerGame()),
                    elegir(a.foulsPerGame(), n.foulsPerGame()), elegir(a.offsideWonPerGame(), n.offsideWonPerGame()),
                    elegir(a.clearancePerGame(), n.clearancePerGame()), elegir(a.wasDribbledPerGame(), n.wasDribbledPerGame()),
                    elegir(a.outfielderBlockPerGame(), n.outfielderBlockPerGame()), elegir(a.goalOwn(), n.goalOwn()));
        }

        private static com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasPases pases(
                com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasPases a,
                com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasPases n) {
            return new com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasPases(
                    elegir(a.totalPassesPerGame(), n.totalPassesPerGame()), elegir(a.passSuccess(), n.passSuccess()),
                    elegir(a.accurateCrossesPerGame(), n.accurateCrossesPerGame()),
                    elegir(a.accurateLongPassPerGame(), n.accurateLongPassPerGame()),
                    elegir(a.accurateThroughBallPerGame(), n.accurateThroughBallPerGame()));
        }


        private static <T> T elegir(T anterior, T nueva) {
            return nueva != null ? nueva : anterior;
        }
        public String posicion() { return posicion; }
        public Map<com.devchesterunited.devchesterunitedv2.modelo.estadisticas.FamiliaEstadistica,
                MetricasEstadisticasJugador> familias() { return Map.copyOf(familias); }
    }
}
