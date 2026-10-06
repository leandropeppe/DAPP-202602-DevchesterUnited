package com.devchesterunited.devchesterunitedv2.persistence;

import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.IdentidadExternaEquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.JugadorEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class SincronizacionRepositoryIntegrationTest extends PostgresTestSupport {
    private static final Instant AHORA = Instant.parse("2026-09-16T12:00:00Z");
    private static final UUID EQUIPO_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final UUID JUGADOR_ID = UUID.fromString("90000000-0000-0000-0000-000000000002");

    @Autowired EquipoRepository equipos;
    @Autowired IdentidadExternaEquipoRepository identidadesEquipos;
    @Autowired JugadorRepository jugadores;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @Test
    void persisteEquipoJugadorAsociadoYAdmiteDorsalNulo() {
        EquipoEntity equipo = equipos.saveAndFlush(new EquipoEntity(EQUIPO_ID, "Equipo importado", "EI", "EIM", "LaLiga",
                "https://img.example/equipo.png", true, 2026L, LocalDate.of(2026, 8, 1),
                LocalDate.of(2027, 5, 31), AHORA, AHORA));
        identidadesEquipos.saveAndFlush(new IdentidadExternaEquipoEntity(
                UUID.randomUUID(), equipo, ProveedorIdentidadExterna.FOOTBALL_DATA, 990001L));
        JugadorEntity jugador = new JugadorEntity(JUGADOR_ID, "Nombre Apellido", EQUIPO_ID, "Forward", null,
                "Argentina", true);
        jugador.conservarAuditoria(AHORA, AHORA);
        jugadores.saveAndFlush(jugador);

        var persistido = jugadores.findById(JUGADOR_ID).orElseThrow();
        assertThat(persistido.getEquipoId()).isEqualTo(EQUIPO_ID);
        assertThat(persistido.getNumeroCamiseta()).isNull();
        assertThat(jugadores.findAllByEquipoId(EQUIPO_ID)).extracting(JugadorEntity::getId).containsExactly(JUGADOR_ID);
        assertThat(identidadesEquipos.findByProveedorAndExternalId(
                ProveedorIdentidadExterna.FOOTBALL_DATA, 990001L)).isPresent();
    }

    @Test
    void impideIdentidadesExternasDuplicadasPorProveedorYExternalId() {
        EquipoEntity equipo = equipos.saveAndFlush(new EquipoEntity(
                EQUIPO_ID, "Equipo unico", "EU", "EUN", "Serie A", null, true,
                2026L, null, null, AHORA, AHORA));
        identidadesEquipos.saveAndFlush(new IdentidadExternaEquipoEntity(
                UUID.randomUUID(), equipo, ProveedorIdentidadExterna.FOOTBALL_DATA, 990003L));

        UUID nuevoId = UUID.randomUUID();
        var equipoRepetido = equipos.saveAndFlush(new EquipoEntity(
                nuevoId, "Repetido", "R", "REP", "Serie A", null, true,
                2026L, null, null, AHORA, AHORA));

        assertThatThrownBy(() -> identidadesEquipos.saveAndFlush(new IdentidadExternaEquipoEntity(
                UUID.randomUUID(), equipoRepetido, ProveedorIdentidadExterna.FOOTBALL_DATA, 990003L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void impideDosIdentidadesDelMismoProveedorParaUnEquipo() {
        equipos.saveAndFlush(new EquipoEntity(
                EQUIPO_ID,
                "Equipo unico",
                "EU",
                "EUN",
                "Serie A",
                null,
                true,
                2026L,
                null,
                null,
                AHORA,
                AHORA
        ));

        var equipo = equipos.findById(EQUIPO_ID).orElseThrow();
        identidadesEquipos.saveAndFlush(new IdentidadExternaEquipoEntity(
                UUID.randomUUID(), equipo, ProveedorIdentidadExterna.FOOTBALL_DATA, 990004L));

        assertThatThrownBy(() -> identidadesEquipos.saveAndFlush(new IdentidadExternaEquipoEntity(
                UUID.randomUUID(), equipo, ProveedorIdentidadExterna.FOOTBALL_DATA, 990005L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void permiteInactivarYReactivarEquipos() {
        equipos.saveAndFlush(new EquipoEntity(EQUIPO_ID, "Equipo estado", "EE", "EST", "Serie A",
                null, true, 2026L, null, null, AHORA, AHORA));
        jdbc.update("UPDATE equipos SET is_active = false, updated_at = ? WHERE id = ?",
                java.sql.Timestamp.from(AHORA.plusSeconds(1)), EQUIPO_ID);
        entityManager.clear();
        assertThat(equipos.findById(EQUIPO_ID).orElseThrow().isActivo()).isFalse();
        jdbc.update("UPDATE equipos SET is_active = true, updated_at = ? WHERE id = ?",
                java.sql.Timestamp.from(AHORA.plusSeconds(2)), EQUIPO_ID);
        entityManager.clear();
        assertThat(equipos.findAllByActivoTrue()).extracting(EquipoEntity::getId).contains(EQUIPO_ID);
    }

    @Test
    void conservaUuidHistoricoDeV3YAsociacionNormalizada() {
        UUID historico = UUID.fromString("30000000-0000-0000-0000-000000000001");
        var existente = jugadores.findById(historico).orElseThrow();
        assertThat(existente.getId()).isEqualTo(historico);
        assertThat(existente.getEquipoId()).isNotNull();
        assertThat(existente.getId()).isEqualTo(historico);
    }
}
