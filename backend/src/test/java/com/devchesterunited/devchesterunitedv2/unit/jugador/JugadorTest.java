package com.devchesterunited.devchesterunitedv2.unit.jugador;

import static org.assertj.core.api.Assertions.*;

import com.devchesterunited.devchesterunitedv2.exception.JugadorException;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.Jugador;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JugadorTest {
    private static final UUID ID = UUID.randomUUID();
    private static final UUID EQUIPO = UUID.randomUUID();
    private static final Instant AHORA = Instant.parse("2026-09-16T12:00:00Z");

    @Test
    void construccionValidaConservaEstadoInterno() {
        var jugador = crear(ID, "Julián Álvarez", "Forward", 9, "Argentina");
        assertThat(jugador.getId()).isEqualTo(ID);
        assertThat(jugador.getNombre()).isEqualTo("Julián Álvarez");
        assertThat(jugador.getEquipoId()).isEqualTo(EQUIPO);
        assertThat(jugador.getPosicion()).isEqualTo("Forward");
        assertThat(jugador.getDorsal()).isEqualTo(9);
        assertThat(jugador.getNacionalidad()).isEqualTo("Argentina");
        assertThat(jugador.isActivo()).isTrue();
        assertThat(jugador.getCreadoEn()).isEqualTo(AHORA);
        assertThat(jugador.getActualizadoEn()).isEqualTo(AHORA);
    }

    @Test
    void admiteDorsalNulo() {
        assertThat(crear(ID, "Jugador", "Defender", null, "Argentina").getDorsal()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Forward", "Midfielder", "Defender", "Goalkeeper"})
    void admitePosicionesCanonicas(String posicion) {
        assertThat(crear(ID, "Jugador", posicion, 9, "Argentina").getPosicion()).isEqualTo(posicion);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 100, -1, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void rechazaDorsalFueraDeRango(int dorsal) {
        assertThatThrownBy(() -> crear(ID, "Jugador", "Forward", dorsal, "Argentina"))
                .isInstanceOf(JugadorException.class);
    }

    @Test
    void desactivarYReactivarPreservanEstadoInmutable() {
        var jugador = crear(ID, "Jugador", "Defender", null, "Argentina");
        var desactivado = jugador.desactivar(AHORA.plusSeconds(1));
        var reactivado = desactivado.reactivar(AHORA.plusSeconds(2));
        assertThat(desactivado.isActivo()).isFalse();
        assertThat(reactivado.isActivo()).isTrue();
        assertThat(reactivado.getId()).isEqualTo(ID);
        assertThat(reactivado.getNombre()).isEqualTo(jugador.getNombre());
        assertThat(reactivado.getEquipoId()).isEqualTo(EQUIPO);
        assertThat(reactivado.getDorsal()).isNull();
        assertThat(reactivado.getCreadoEn()).isEqualTo(AHORA);
        assertThat(reactivado.getActualizadoEn()).isEqualTo(AHORA.plusSeconds(2));
    }

    private static Jugador crear(UUID id, String nombre, String posicion, Integer dorsal, String nacionalidad) {
        return new Jugador(id, nombre, EQUIPO, posicion, dorsal, nacionalidad, AHORA);
    }
}
