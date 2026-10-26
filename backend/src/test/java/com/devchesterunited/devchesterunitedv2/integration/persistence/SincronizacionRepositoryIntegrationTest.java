package com.devchesterunited.devchesterunitedv2.integration.persistence;

import com.devchesterunited.devchesterunitedv2.modelo.equipo.Equipo;
import static org.assertj.core.api.Assertions.*;

import com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.EquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.IdentidadExternaEquipoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.EquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.repository.IdentidadExternaEquipoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @BeforeEach
    void prepararJugadorHistorico() {
        UUID teamId = UUID.fromString("30000000-0000-0000-0000-000000000010");
        jdbc.update("""
                INSERT INTO equipos (id, name, league, is_active, created_at, updated_at)
                VALUES (?, 'Equipo historico', 'LaLiga', TRUE, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, teamId, java.sql.Timestamp.from(AHORA), java.sql.Timestamp.from(AHORA));
        jdbc.update("""
                INSERT INTO jugadores
                    (id, name, team_id, position, jersey_number, nationality, is_active, created_at, updated_at)
                VALUES (?, 'Jugador historico', ?, 'Forward', 10, 'Argentina', TRUE, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, UUID.fromString("30000000-0000-0000-0000-000000000001"), teamId,
                java.sql.Timestamp.from(AHORA), java.sql.Timestamp.from(AHORA));
    }

    @Test
    void persisteEquipoJugadorAsociadoYAdmiteDorsalNulo() {
        EquipoEntity equipo = equipos.saveAndFlush(EquipoEntity.builder().id(EQUIPO_ID).nombre("Equipo importado")
                .nombreCorto("EI").sigla("EIM").liga("LaLiga").escudoUrl("https://img.example/equipo.png")
                .activo(true).temporadaExternaId(2026L).inicioTemporada(LocalDate.of(2026, 8, 1))
                .finTemporada(LocalDate.of(2027, 5, 31)).creadoEn(AHORA).actualizadoEn(AHORA).build());
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
        EquipoEntity equipo = equipos.saveAndFlush(EquipoEntity.builder().id(EQUIPO_ID).nombre("Equipo unico")
                .nombreCorto("EU").sigla("EUN").liga("Serie A").escudoUrl(null).activo(true)
                .temporadaExternaId(2026L).inicioTemporada(null).finTemporada(null)
                .creadoEn(AHORA).actualizadoEn(AHORA).build());
        identidadesEquipos.saveAndFlush(new IdentidadExternaEquipoEntity(
                UUID.randomUUID(), equipo, ProveedorIdentidadExterna.FOOTBALL_DATA, 990003L));

        UUID nuevoId = UUID.randomUUID();
        var equipoRepetido = equipos.saveAndFlush(EquipoEntity.builder().id(nuevoId).nombre("Repetido")
                .nombreCorto("R").sigla("REP").liga("Serie A").escudoUrl(null).activo(true)
                .temporadaExternaId(2026L).inicioTemporada(null).finTemporada(null)
                .creadoEn(AHORA).actualizadoEn(AHORA).build());

        IdentidadExternaEquipoEntity identidadDuplicada = new IdentidadExternaEquipoEntity(
                UUID.randomUUID(), equipoRepetido, ProveedorIdentidadExterna.FOOTBALL_DATA, 990003L);
        assertThatThrownBy(() -> identidadesEquipos.saveAndFlush(identidadDuplicada))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void impideDosIdentidadesDelMismoProveedorParaUnEquipo() {
        equipos.saveAndFlush(EquipoEntity.builder().id(EQUIPO_ID).nombre("Equipo unico")
                .nombreCorto("EU").sigla("EUN").liga("Serie A").escudoUrl(null).activo(true)
                .temporadaExternaId(2026L).inicioTemporada(null).finTemporada(null)
                .creadoEn(AHORA).actualizadoEn(AHORA).build());

        var equipo = equipos.findById(EQUIPO_ID).orElseThrow();
        identidadesEquipos.saveAndFlush(new IdentidadExternaEquipoEntity(
                UUID.randomUUID(), equipo, ProveedorIdentidadExterna.FOOTBALL_DATA, 990004L));

        IdentidadExternaEquipoEntity identidadSegundoProveedor = new IdentidadExternaEquipoEntity(
                UUID.randomUUID(), equipo, ProveedorIdentidadExterna.FOOTBALL_DATA, 990005L);
        assertThatThrownBy(() -> identidadesEquipos.saveAndFlush(identidadSegundoProveedor))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void permiteInactivarYReactivarEquipos() {
        equipos.saveAndFlush(EquipoEntity.builder().id(EQUIPO_ID).nombre("Equipo estado")
                .nombreCorto("EE").sigla("EST").liga("Serie A").escudoUrl(null).activo(true)
                .temporadaExternaId(2026L).inicioTemporada(null).finTemporada(null)
                .creadoEn(AHORA).actualizadoEn(AHORA).build());
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
