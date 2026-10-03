package com.devchesterunited.devchesterunitedv2.modelo;

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

        assertThat(identidad.getJugadorId()).isEqualTo(jugador);
        assertThat(identidad.getProveedor()).isEqualTo(ProveedorIdentidadExterna.FOOTBALL_DATA);
        assertThat(identidad.getExternalId()).isEqualTo(42);
    }

    @Test
    void rechazaIdentidadInvalida() {
        assertThatThrownBy(() -> new IdentidadExternaJugador(UUID.randomUUID(),
                ProveedorIdentidadExterna.WHOSCORED, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
