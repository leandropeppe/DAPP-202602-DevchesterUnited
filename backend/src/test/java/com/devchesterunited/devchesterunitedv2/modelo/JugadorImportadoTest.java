package com.devchesterunited.devchesterunitedv2.modelo;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class JugadorImportadoTest {
    private static final UUID EQUIPO = UUID.randomUUID();
    private static final Instant AHORA = Instant.parse("2026-09-16T12:00:00Z");

    @Test
    void admiteDorsalNuloYApellidoAusente() {
        var jugador = Jugador.importado(UUID.randomUUID(), 7L, "Nombre", null, EQUIPO,
                "Forward", null, "Argentina", AHORA);
        assertThat(jugador.getIdExterno()).isEqualTo(7L);
        assertThat(jugador.getEquipoId()).isEqualTo(EQUIPO);
        assertThat(jugador.getApellido()).isEmpty();
        assertThat(jugador.getDorsal()).isZero();
        assertThat(jugador.isActivo()).isTrue();
    }

    @Test
    void desactivarYReactivarConservanIdentidadYPertenencia() {
        var jugador = Jugador.importado(UUID.randomUUID(), 7L, "Nombre", "Apellido", EQUIPO,
                "Defender", 99, "Argentina", AHORA);
        var desactivado = jugador.desactivar(AHORA.plusSeconds(1));
        var reactivado = desactivado.reactivar(AHORA.plusSeconds(2));
        assertThat(desactivado.isActivo()).isFalse();
        assertThat(reactivado.isActivo()).isTrue();
        assertThat(reactivado.getId()).isEqualTo(jugador.getId());
        assertThat(reactivado.getEquipoId()).isEqualTo(EQUIPO);
        assertThat(reactivado.getIdExterno()).isEqualTo(7L);
    }

    @Test
    void rechazaIdentidadEquipoPosicionYNacionalidadInvalidos() {
        assertThatThrownBy(() -> Jugador.importado(UUID.randomUUID(), 0L, "N", "", EQUIPO,
                "Forward", null, "AR", AHORA)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> Jugador.importado(UUID.randomUUID(), 1L, "N", "", null,
                "Forward", null, "AR", AHORA)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> Jugador.importado(UUID.randomUUID(), 1L, "N", "", EQUIPO,
                "Striker", null, "AR", AHORA)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> Jugador.importado(UUID.randomUUID(), 1L, "N", "", EQUIPO,
                "Forward", 100, "AR", AHORA)).isInstanceOf(RuntimeException.class);
    }
}
