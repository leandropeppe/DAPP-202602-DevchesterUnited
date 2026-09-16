package com.devchesterunited.devchesterunitedv2.modelo;

import com.devchesterunited.devchesterunitedv2.modelo.exception.UsuarioException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

/** Contrato Java propuesto por T012: constructor completo y getters en español. */
class UsuarioTest {
    private static final Instant FECHA = Instant.parse("2026-09-01T12:00:00Z");
    private static final String HASH = "$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW";

    @Test
    void conservaIdentidadHashYFechasValidas() {
        UUID id = UUID.randomUUID();
        for (Instant actualizado : new Instant[]{FECHA, FECHA.plusSeconds(1)}) {
            var usuario = new Usuario(id, "jugador", "jugador@example.com", HASH, FECHA, actualizado);
            assertThat(usuario.getId()).isEqualTo(id);
            assertThat(usuario.getNombreUsuario()).isEqualTo("jugador");
            assertThat(usuario.getCorreo()).isEqualTo("jugador@example.com");
            assertThat(usuario.getHashContrasena()).isEqualTo(HASH);
            assertThat(usuario.getCreadoEn()).isEqualTo(FECHA);
            assertThat(usuario.getActualizadoEn()).isEqualTo(actualizado);
            assertThat(usuario.toString()).doesNotContain(HASH);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rechazaIdentidadOHashVaciosConExcepcionPropia(String vacio) {
        assertThatThrownBy(() -> new Usuario(UUID.randomUUID(), vacio, "a@example.com", HASH, FECHA, FECHA))
                .isInstanceOf(UsuarioException.class);
        assertThatThrownBy(() -> new Usuario(UUID.randomUUID(), "jugador", vacio, HASH, FECHA, FECHA))
                .isInstanceOf(UsuarioException.class);
        assertThatThrownBy(() -> new Usuario(UUID.randomUUID(), "jugador", "a@example.com", vacio, FECHA, FECHA))
                .isInstanceOf(UsuarioException.class);
    }

    @Test
    void rechazaIdYFechasNulosOActualizacionAnterior() {
        assertThatThrownBy(() -> new Usuario(null, "jugador", "a@example.com", HASH, FECHA, FECHA))
                .isInstanceOf(UsuarioException.class);
        assertThatThrownBy(() -> new Usuario(UUID.randomUUID(), "jugador", "a@example.com", HASH, null, FECHA))
                .isInstanceOf(UsuarioException.class);
        assertThatThrownBy(() -> new Usuario(UUID.randomUUID(), "jugador", "a@example.com", HASH, FECHA, null))
                .isInstanceOf(UsuarioException.class);
        assertThatThrownBy(() -> new Usuario(UUID.randomUUID(), "jugador", "a@example.com", HASH, FECHA, FECHA.minusNanos(1)))
                .isInstanceOf(UsuarioException.class);
    }
}
