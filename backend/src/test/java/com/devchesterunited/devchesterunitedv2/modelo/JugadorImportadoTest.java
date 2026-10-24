package com.devchesterunited.devchesterunitedv2.modelo.estadisticas;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.Jugador;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class JugadorImportadoTest {
    private static final UUID EQUIPO = UUID.randomUUID();
    private static final Instant AHORA = Instant.parse("2026-09-16T12:00:00Z");

    @Test
    void admiteDorsalNuloYPosicionNula() {
        var jugador = new Jugador(UUID.randomUUID(), "Nombre Completo", EQUIPO,
                null, null, "Argentina", AHORA);
        assertThat(jugador.getEquipoId()).isEqualTo(EQUIPO);
        assertThat(jugador.getDorsal()).isNull();
        assertThat(jugador.isActivo()).isTrue();
    }

    @Test
    void desactivarYReactivarConservanIdentidadYPertenencia() {
        var jugador = new Jugador(UUID.randomUUID(), "Nombre Apellido", EQUIPO,
                "Defender", 99, "Argentina", AHORA);
        var desactivado = jugador.desactivar(AHORA.plusSeconds(1));
        var reactivado = desactivado.reactivar(AHORA.plusSeconds(2));
        assertThat(desactivado.isActivo()).isFalse();
        assertThat(reactivado.isActivo()).isTrue();
        assertThat(reactivado.getId()).isEqualTo(jugador.getId());
        assertThat(reactivado.getEquipoId()).isEqualTo(EQUIPO);
        assertThat(reactivado.getDorsal()).isEqualTo(99);
    }

    @Test
    void rechazaEquipoPosicionYNacionalidadInvalidos() {
        UUID jugadorSinEquipo = UUID.randomUUID();
        UUID jugadorConPosicionInvalida = UUID.randomUUID();
        UUID jugadorConDorsalInvalido = UUID.randomUUID();

        assertThatThrownBy(() -> new Jugador(
                jugadorSinEquipo, "N", null,
                "Forward", null, "AR", AHORA))
                .isInstanceOf(RuntimeException.class);

        assertThatThrownBy(() -> new Jugador(
                jugadorConPosicionInvalida, "N", EQUIPO,
                "Striker", null, "AR", AHORA))
                .isInstanceOf(RuntimeException.class);

        assertThatThrownBy(() -> new Jugador(
                jugadorConDorsalInvalido, "N", EQUIPO,
                "Forward", 100, "AR", AHORA))
                .isInstanceOf(RuntimeException.class);
    }
}
