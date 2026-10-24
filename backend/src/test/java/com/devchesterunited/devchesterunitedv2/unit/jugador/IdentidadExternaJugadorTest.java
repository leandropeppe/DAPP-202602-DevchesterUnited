package com.devchesterunited.devchesterunitedv2.modelo.estadisticas;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.IdentidadExternaJugador;
import com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentidadExternaJugadorTest {
    @Test
    void representaLaIdentidadSeparadaDelUuidLocal() {
        UUID jugador = UUID.randomUUID();
        var identidad = new IdentidadExternaJugador(jugador,
                ProveedorIdentidadExterna.FOOTBALL_DATA, 42);

        assertThat(identidad.jugadorId()).isEqualTo(jugador);
        assertThat(identidad.proveedor()).isEqualTo(ProveedorIdentidadExterna.FOOTBALL_DATA);
        assertThat(identidad.externalId()).isEqualTo(42);
    }

    @Test
    void rechazaJugadorIdNulo() {
        assertThatThrownBy(() -> new IdentidadExternaJugador(null,
                ProveedorIdentidadExterna.WHOSCORED, 42))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaProveedorNulo() {
        UUID jugadorId = UUID.randomUUID();

        assertThatThrownBy(() -> new IdentidadExternaJugador(
                jugadorId,
                null,
                42))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaExternalIdInvalido() {
        UUID jugadorId = UUID.randomUUID();

        assertThatThrownBy(() -> new IdentidadExternaJugador(
                jugadorId,
                ProveedorIdentidadExterna.WHOSCORED,
                0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
