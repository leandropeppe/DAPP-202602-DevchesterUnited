package com.devchesterunited.devchesterunitedv2.persistence;

import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EquipoEntity;
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
    @Autowired JugadorRepository jugadores;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @Test
    void persisteEquipoJugadorAsociadoYAdmiteDorsalNulo() {
        equipos.saveAndFlush(new EquipoEntity(EQUIPO_ID, 990001L, "Equipo importado", "EI", "EIM", "LaLiga",
                "https://img.example/equipo.png", true, 2026L, LocalDate.of(2026, 8, 1),
                LocalDate.of(2027, 5, 31), AHORA, AHORA));
        jugadores.saveAndFlush(new JugadorEntity(JUGADOR_ID, 990002L, "Nombre", "Apellido", EQUIPO_ID, "Forward", null,
                "Argentina", true, AHORA, AHORA, "LaLiga", "Equipo importado"));

        var jugador = jugadores.findByIdExterno(990002L).orElseThrow();
        assertThat(jugador.getEquipoId()).isEqualTo(EQUIPO_ID);
        assertThat(jugador.getNumeroCamiseta()).isNull();
        assertThat(jugadores.findAllByEquipoId(EQUIPO_ID)).extracting(JugadorEntity::getId).containsExactly(JUGADOR_ID);
        assertThat(equipos.findByIdExterno(990001L)).isPresent();
    }

    @Test
    void impideIdsExternosDuplicados() {
        equipos.saveAndFlush(new EquipoEntity(EQUIPO_ID, 990003L, "Equipo unico", "EU", "EUN", "Serie A",
                null, true, 2026L, null, null, AHORA, AHORA));
        assertThatThrownBy(() -> equipos.saveAndFlush(new EquipoEntity(UUID.randomUUID(), 990003L, "Repetido", "R", "REP",
                "Serie A", null, true, 2026L, null, null, AHORA, AHORA)))
                .isInstanceOf(DataIntegrityViolationException.class);

    }

    @Test
    void permiteInactivarYReactivarEquipos() {
        equipos.saveAndFlush(new EquipoEntity(EQUIPO_ID, 990004L, "Equipo estado", "EE", "EST", "Serie A",
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
        assertThat(existente.getIdExterno()).isNull();
    }
}
