package com.devchesterunited.devchesterunitedv2.unit.estadisticas;

import com.devchesterunited.devchesterunitedv2.modelo.equipo.Equipo;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.FamiliaEstadistica;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.IdentidadEstadisticaJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.MetricasEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.RegistroEstadisticasJugador;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.Jugador;

import com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.ConsolidadorEstadisticasJugador;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConsolidadorEstadisticasJugadorIdempotenciaTest {
    @Test
    void consolidarDosVecesLosMismosRegistrosProduceLaMismaFotografia() {
        UUID jugadorId = UUID.randomUUID();
        var registros = List.of(
                registro(FamiliaEstadistica.GENERAL, 90, 1, null),
                registro(FamiliaEstadistica.GENERAL, null, null, null),
                registro(FamiliaEstadistica.OFENSIVA, null, null, BigDecimal.valueOf(3)),
                registro(FamiliaEstadistica.OFENSIVA, null, null, null),
                registro(FamiliaEstadistica.DEFENSIVA, 120, 2, null),
                registro(FamiliaEstadistica.DEFENSIVA, null, null, null));
        var consolidador = new ConsolidadorEstadisticasJugador();

        var primera = consolidador.consolidar(registros.stream()
                .map(registro -> new ConsolidadorEstadisticasJugador.RegistroResuelto(jugadorId, registro))
                .toList());
        var segunda = consolidador.consolidar(registros.stream()
                .map(registro -> new ConsolidadorEstadisticasJugador.RegistroResuelto(jugadorId, registro))
                .toList());

        assertThat(segunda.jugadores().get(jugadorId).familias())
                .isEqualTo(primera.jugadores().get(jugadorId).familias());
        assertThat(segunda.jugadores().get(jugadorId).familias()).containsKeys(
                FamiliaEstadistica.GENERAL, FamiliaEstadistica.OFENSIVA, FamiliaEstadistica.DEFENSIVA);
        assertThat(segunda.jugadores().get(jugadorId).familias().get(FamiliaEstadistica.GENERAL)
                .minutosJugados()).isEqualTo(90);
        assertThat(segunda.jugadores().get(jugadorId).familias().get(FamiliaEstadistica.OFENSIVA)
                .tiros()).isEqualByComparingTo("3");
        assertThat(segunda.jugadores().get(jugadorId).familias().get(FamiliaEstadistica.DEFENSIVA)
                .minutosJugados()).isEqualTo(120);
    }

    private static RegistroEstadisticasJugador registro(FamiliaEstadistica familia, Integer minutos,
            Integer goles, BigDecimal tiros) {
        return new RegistroEstadisticasJugador(
                new IdentidadEstadisticaJugador(44L, "Jugador", "Forward", "Argentina", 77L, "Equipo"),
                familia,
                new MetricasEstadisticasJugador(minutos, goles, null, tiros, null, null, null, null, null, null));
    }
}
