package com.devchesterunited.devchesterunitedv2.persistence.mapper;

import com.devchesterunited.devchesterunitedv2.modelo.Equipo;
import com.devchesterunited.devchesterunitedv2.modelo.Jugador;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class JugadorMapperTest {
    private static final Instant CREADO = Instant.parse("2026-09-15T10:00:00Z");
    private static final Instant ACTUALIZADO = Instant.parse("2026-09-16T12:00:00Z");
    private final JugadorMapper mapper = new JugadorMapper();

    @Test
    void mapeaJugadorNuevoConEquipoYColumnasLegacy() {
        UUID jugadorId = UUID.randomUUID();
        UUID equipoId = UUID.randomUUID();
        Jugador jugador = new Jugador(jugadorId, "Lionel Messi", equipoId,
                "Forward", 10, "Argentina", ACTUALIZADO);
        Equipo equipo = equipo(equipoId);

        JugadorEntity resultado = mapper.toEntity(jugador, 77L, equipo, equipoId, null, ACTUALIZADO);

        assertThat(resultado.getId()).isEqualTo(jugadorId);
        assertThat(resultado.getIdExterno()).isEqualTo(77L);
        assertThat(resultado.getEquipoId()).isEqualTo(equipoId);
        assertThat(resultado.getLiga()).isEqualTo("Premier League");
        assertThat(resultado.getEquipo()).isEqualTo("Manchester United");
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
        JugadorEntity anterior = new JugadorEntity(anteriorId, 88L, "Nombre anterior", equipoId,
                "Forward", 9, "Argentina", false, CREADO, CREADO,
                "Liga anterior", "Equipo anterior");

        JugadorEntity resultado = mapper.toEntity(jugador, 88L, equipo(equipoId), equipoId, anterior, ACTUALIZADO);

        assertThat(resultado.getId()).isEqualTo(anteriorId);
        assertThat(resultado.getIdExterno()).isEqualTo(88L);
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
        JugadorEntity actual = new JugadorEntity(jugadorId, 99L, "Jugador", equipoId,
                "Defender", 5, "Chile", true, CREADO, CREADO,
                "Liga", "Equipo");

        JugadorEntity resultado = mapper.toInactiveEntity(actual, ACTUALIZADO);

        assertThat(resultado.getId()).isEqualTo(jugadorId);
        assertThat(resultado.getIdExterno()).isEqualTo(99L);
        assertThat(resultado.getEquipoId()).isEqualTo(equipoId);
        assertThat(resultado.getNumeroCamiseta()).isEqualTo(5);
        assertThat(resultado.getLiga()).isEqualTo("Liga");
        assertThat(resultado.getEquipo()).isEqualTo("Equipo");
        assertThat(resultado.isActivo()).isFalse();
        assertThat(resultado.getCreadoEn()).isEqualTo(CREADO);
        assertThat(resultado.getActualizadoEn()).isEqualTo(ACTUALIZADO);
    }

    private static Equipo equipo(UUID id) {
        return Equipo.importado(id, 12L, "Manchester United", "Man Utd", "MUN",
                "Premier League", null, 2026, null, null, CREADO);
    }
}
