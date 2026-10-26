package com.devchesterunited.devchesterunitedv2.unit.estadisticas;

import com.devchesterunited.devchesterunitedv2.exception.JugadorException;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.EstadoJugador;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EstadoJugadorTest {
    private static final Instant CREADO = Instant.parse("2026-09-15T10:00:00Z");
    private static final Instant ACTUALIZADO = Instant.parse("2026-09-16T12:00:00Z");

    @Test
    void creaEstadoActivoConTimestampsInicialesIguales() {
        EstadoJugador estado = EstadoJugador.nuevo(CREADO);

        assertThat(estado.isActivo()).isTrue();
        assertThat(estado.getCreadoEn()).isEqualTo(CREADO);
        assertThat(estado.getActualizadoEn()).isEqualTo(CREADO);
    }

    @Test
    void reconstruyeEstadoInactivoConservandoAuditoria() {
        EstadoJugador estado = EstadoJugador.desdePersistencia(false, CREADO, ACTUALIZADO);

        assertThat(estado.isActivo()).isFalse();
        assertThat(estado.getCreadoEn()).isEqualTo(CREADO);
        assertThat(estado.getActualizadoEn()).isEqualTo(ACTUALIZADO);
    }

    @Test
    void desactivarActualizaSoloEstadoYFechaDeActualizacion() {
        EstadoJugador inicial = EstadoJugador.nuevo(CREADO);

        EstadoJugador desactivado = inicial.desactivar(ACTUALIZADO);

        assertThat(desactivado.isActivo()).isFalse();
        assertThat(desactivado.getCreadoEn()).isEqualTo(CREADO);
        assertThat(desactivado.getActualizadoEn()).isEqualTo(ACTUALIZADO);
        assertThat(inicial.isActivo()).isTrue();
    }

    @Test
    void reactivarActualizaEstadoYConservaFechaDeCreacion() {
        EstadoJugador desactivado = EstadoJugador.desdePersistencia(false, CREADO, CREADO);

        EstadoJugador reactivado = desactivado.reactivar(ACTUALIZADO);

        assertThat(reactivado.isActivo()).isTrue();
        assertThat(reactivado.getCreadoEn()).isEqualTo(CREADO);
        assertThat(reactivado.getActualizadoEn()).isEqualTo(ACTUALIZADO);
        assertThat(desactivado.isActivo()).isFalse();
    }

    @Test
    void rechazaFechasIncoherentes() {
        Instant fechaAnterior = CREADO.minusSeconds(1);
        EstadoJugador estado = EstadoJugador.nuevo(CREADO);

        assertThatThrownBy(() ->
                EstadoJugador.desdePersistencia(true, ACTUALIZADO, CREADO))
                .isInstanceOf(JugadorException.class);

        assertThatThrownBy(() ->
                EstadoJugador.nuevo(null))
                .isInstanceOf(JugadorException.class);

        assertThatThrownBy(() ->
                estado.desactivar(fechaAnterior))
                .isInstanceOf(JugadorException.class);
    }
}
