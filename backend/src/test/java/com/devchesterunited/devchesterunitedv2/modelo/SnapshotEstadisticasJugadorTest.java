package com.devchesterunited.devchesterunitedv2.modelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class SnapshotEstadisticasJugadorTest {
    @Test
    void representaUnaCompeticionConRegistrosInmutables() {
        var registro = new RegistroEstadisticasJugador(
                new IdentidadEstadisticaJugador(10L, "Jugador", null, null, 20L, "Equipo"),
                FamiliaEstadistica.GENERAL,
                new MetricasEstadisticasJugador(90, 1, 1, null, null, null, null, null, null, null));

        var snapshot = new SnapshotEstadisticasJugador(
                CompeticionEstadistica.PREMIER_LEAGUE, List.of(registro));

        assertThat(snapshot.competicion()).isEqualTo(CompeticionEstadistica.PREMIER_LEAGUE);
        assertThat(snapshot.registros()).containsExactly(registro);
        var registros = snapshot.registros();
        assertThatThrownBy(() -> registros.add(registro))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rechazaRegistrosNulos() {
        var nullRecords = java.util.Arrays.asList((RegistroEstadisticasJugador) null);
        assertThatThrownBy(() -> new SnapshotEstadisticasJugador(
                CompeticionEstadistica.PREMIER_LEAGUE, nullRecords))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
