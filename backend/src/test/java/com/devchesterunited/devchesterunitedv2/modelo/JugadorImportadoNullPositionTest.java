package com.devchesterunited.devchesterunitedv2.modelo;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JugadorImportadoNullPositionTest {
    @Test
    void admitePosicionNulaCuandoElProveedorNoLaClasifica() {
        var jugador = Jugador.importado(UUID.randomUUID(), 42L, "Jugador Sin posicion",
                UUID.randomUUID(), null, null, "Argentina", Instant.now());

        assertThat(jugador.getPosicion()).isNull();
    }
}
