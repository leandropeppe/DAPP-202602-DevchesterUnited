package com.devchesterunited.devchesterunitedv2.modelo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class EstadisticasPaginaTest {
    @Test
    void copiaLaListaDeRegistros() {
        var identidad = new IdentidadEstadisticaJugador(
                10L, "Jugador", "Forward", "Argentina", 20L, "Equipo");
        var registro = new RegistroEstadisticasJugador(
                identidad, FamiliaEstadistica.GENERAL,
                new MetricasEstadisticasJugador(null, null, null, null, null, null,
                        null, null, null, null));
        var pagina = new PaginaEstadisticasJugador(
                CompeticionEstadistica.PREMIER_LEAGUE, FamiliaEstadistica.GENERAL,
                1, 50, 1, 1, List.of(registro));

        assertThat(pagina.registros()).containsExactly(registro);
    }

    @Test
    void rechazaMetadataInconsistente() {
        assertThatThrownBy(() -> new PaginaEstadisticasJugador(
                CompeticionEstadistica.PREMIER_LEAGUE, FamiliaEstadistica.GENERAL,
                2, 50, 1, 1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaIdentificadorExternoNoPositivo() {
        assertThatThrownBy(() -> new IdentidadEstadisticaJugador(
                0L, "Jugador", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
