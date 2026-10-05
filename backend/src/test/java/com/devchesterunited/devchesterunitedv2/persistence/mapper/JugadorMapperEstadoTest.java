package com.devchesterunited.devchesterunitedv2.persistence.mapper;

import com.devchesterunited.devchesterunitedv2.modelo.Jugador;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class JugadorMapperEstadoTest {
    private static final Instant CREADO = Instant.parse("2026-09-15T10:00:00Z");
    private static final Instant ACTUALIZADO = Instant.parse("2026-09-16T12:00:00Z");

    @Test
    void reconstruyeJugadorYEstadoSinPerderAuditoria() {
        UUID id = UUID.randomUUID();
        UUID equipoId = UUID.randomUUID();
        JugadorEntity entidad = new JugadorEntity(id, "Jugador", equipoId,
                "Forward", 10, "Argentina", false);
        entidad.conservarAuditoria(CREADO, ACTUALIZADO);

        Jugador jugador = new JugadorMapper().toDomain(entidad);

        assertThat(jugador.getId()).isEqualTo(id);
        assertThat(jugador.getEquipoId()).isEqualTo(equipoId);
        assertThat(jugador.getEstado().isActivo()).isFalse();
        assertThat(jugador.getEstado().getCreadoEn()).isEqualTo(CREADO);
        assertThat(jugador.getEstado().getActualizadoEn()).isEqualTo(ACTUALIZADO);
    }
}
