package com.devchesterunited.devchesterunitedv2.unit.jugador;

import com.devchesterunited.devchesterunitedv2.modelo.jugador.Jugador;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.mapper.JugadorMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JugadorMapperTest {
    private static final Instant CREADO = Instant.parse("2026-09-15T10:00:00Z");
    private static final Instant ACTUALIZADO = Instant.parse("2026-09-16T12:00:00Z");
    private final JugadorMapper mapper = new JugadorMapper();

    @Test
    void mapeaJugadorNuevoSoloConEquipoId() {
        UUID jugadorId = UUID.randomUUID();
        UUID equipoId = UUID.randomUUID();
        Jugador jugador = new Jugador(jugadorId, "Lionel Messi", equipoId,
                "Forward", 10, "Argentina", ACTUALIZADO);
        JugadorEntity resultado = mapper.toEntity(jugador, equipoId, null, ACTUALIZADO);

        assertThat(resultado.getId()).isEqualTo(jugadorId);
        assertThat(resultado.getEquipoId()).isEqualTo(equipoId);
        assertThat(resultado.getNumeroCamiseta()).isEqualTo(10);
        assertThat(resultado.getNacionalidad()).isEqualTo("Argentina");
        assertThat(resultado.isActivo()).isTrue();
        assertThat(resultado.getCreadoEn()).isEqualTo(ACTUALIZADO);
        assertThat(resultado.getActualizadoEn()).isEqualTo(ACTUALIZADO);
    }

    @Test
    void mapeaActualizacionConservandoUuidExternalIdYFechaDeCreacion() {
        UUID anteriorId = UUID.randomUUID();
        UUID equipoId = UUID.randomUUID();
        Jugador jugador = new Jugador(UUID.randomUUID(), "Nombre actualizado", equipoId,
                "Midfielder", null, "Uruguay", ACTUALIZADO);
        JugadorEntity anterior = new JugadorEntity(anteriorId, "Nombre anterior", equipoId,
                "Forward", 9, "Argentina", false);
        anterior.conservarAuditoria(CREADO, CREADO);

        JugadorEntity resultado = mapper.toEntity(jugador, equipoId, anterior, ACTUALIZADO);

        assertThat(resultado.getId()).isEqualTo(anteriorId);
        assertThat(resultado.getNombre()).isEqualTo("Nombre actualizado");
        assertThat(resultado.getPosicion()).isEqualTo("Midfielder");
        assertThat(resultado.getNumeroCamiseta()).isNull();
        assertThat(resultado.isActivo()).isTrue();
        assertThat(resultado.getCreadoEn()).isEqualTo(CREADO);
        assertThat(resultado.getActualizadoEn()).isEqualTo(ACTUALIZADO);
    }

    @Test
    void mapeaDesactivacionConservandoDatosPersistidos() {
        UUID jugadorId = UUID.randomUUID();
        UUID equipoId = UUID.randomUUID();
        JugadorEntity actual = new JugadorEntity(jugadorId, "Jugador", equipoId,
                "Defender", 5, "Chile", true);
        actual.conservarAuditoria(CREADO, CREADO);

        JugadorEntity resultado = mapper.toInactiveEntity(actual, ACTUALIZADO);

        assertThat(resultado.getId()).isEqualTo(jugadorId);
        assertThat(resultado.getEquipoId()).isEqualTo(equipoId);
        assertThat(resultado.getNumeroCamiseta()).isEqualTo(5);
        assertThat(resultado.isActivo()).isFalse();
        assertThat(resultado.getCreadoEn()).isEqualTo(CREADO);
        assertThat(resultado.getActualizadoEn()).isEqualTo(ACTUALIZADO);
    }
}
