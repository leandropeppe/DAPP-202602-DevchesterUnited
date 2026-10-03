package com.devchesterunited.devchesterunitedv2.persistence;

import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class IdentidadExternaJugadorRepositoryIntegrationTest extends PostgresTestSupport {
    @Autowired IdentidadExternaJugadorRepository repositorio;
    @Autowired JdbcTemplate jdbc;

    private final UUID jugador = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private final UUID identidadFootballData = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private final UUID identidadWhoScored = UUID.fromString("60000000-0000-0000-0000-000000000002");

    @BeforeEach
    void prepararJugador() {
        jdbc.update("DELETE FROM jugadores WHERE id = ?", jugador);
        jdbc.update("INSERT INTO jugadores (id, name, league, team, position, jersey_number, nationality) "
                + "VALUES (?, 'Jugador Backfill', 'Liga prueba', 'Equipo prueba', 'Forward', 9, 'Argentina')", jugador);
    }

    @Test
    void backfillRegistraExternalIdLegacyComoFootballData() {
        jdbc.update("UPDATE jugadores SET external_id = ? WHERE id = ?", 7010L, jugador);
        jdbc.update("INSERT INTO identidades_externas_jugador "
                + "(id, jugador_id, proveedor, external_id) "
                + "SELECT md5('football-data::' || id::text)::uuid, id, 'FOOTBALL_DATA', external_id "
                + "FROM jugadores WHERE id = ? AND external_id IS NOT NULL", jugador);

        var identidad = repositorio.findByJugador_IdAndProveedor(jugador,
                ProveedorIdentidadExterna.FOOTBALL_DATA);
        assertThat(identidad).isPresent();
        assertThat(identidad.orElseThrow().getExternalId()).isEqualTo(7010L);
    }

    @Test
    void permiteMismoExternalIdEnProveedoresDiferentes() {
        insertarIdentidad(identidadFootballData, ProveedorIdentidadExterna.FOOTBALL_DATA, 7001, jugador);
        UUID otroJugador = UUID.fromString("50000000-0000-0000-0000-000000000002");
        jdbc.update("INSERT INTO jugadores (id, name, league, team, position, jersey_number, nationality) "
                + "VALUES (?, 'Otro Jugador', 'Liga prueba', 'Equipo prueba', 'Forward', 10, 'Argentina')", otroJugador);
        insertarIdentidad(identidadWhoScored, ProveedorIdentidadExterna.WHOSCORED, 7001, otroJugador);

        assertThat(repositorio.findByProveedorAndExternalId(ProveedorIdentidadExterna.FOOTBALL_DATA, 7001L)).isPresent();
        assertThat(repositorio.findByProveedorAndExternalId(ProveedorIdentidadExterna.WHOSCORED, 7001L)).isPresent();
    }

    @Test
    void imponeUnicidadPorProveedorYExternalId() {
        insertarIdentidad(identidadFootballData, ProveedorIdentidadExterna.FOOTBALL_DATA, 7002, jugador);
        UUID otroJugador = UUID.fromString("50000000-0000-0000-0000-000000000003");
        jdbc.update("INSERT INTO jugadores (id, name, league, team, position, jersey_number, nationality) "
                + "VALUES (?, 'Otro Jugador', 'Liga prueba', 'Equipo prueba', 'Forward', 11, 'Argentina')", otroJugador);

        assertThatThrownBy(() -> insertarIdentidad(UUID.randomUUID(),
                ProveedorIdentidadExterna.FOOTBALL_DATA, 7002, otroJugador))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void unJugadorNoPuedeTenerDosIdentidadesDelMismoProveedor() {
        insertarIdentidad(identidadFootballData, ProveedorIdentidadExterna.FOOTBALL_DATA, 7003, jugador);

        assertThatThrownBy(() -> insertarIdentidad(UUID.randomUUID(),
                ProveedorIdentidadExterna.FOOTBALL_DATA, 7004, jugador))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void conservaElUuidLocalAlAsociarLaIdentidad() {
        insertarIdentidad(identidadFootballData, ProveedorIdentidadExterna.FOOTBALL_DATA, 7005, jugador);

        assertThat(jdbc.queryForObject("SELECT id FROM jugadores WHERE external_id IS NULL AND id = ?",
                UUID.class, jugador)).isEqualTo(jugador);
    }

    private void insertarIdentidad(UUID id, ProveedorIdentidadExterna proveedor,
            long externalId, UUID jugadorId) {
        jdbc.update("INSERT INTO identidades_externas_jugador "
                + "(id, jugador_id, proveedor, external_id) VALUES (?, ?, ?, ?)",
                id, jugadorId, proveedor.name(), externalId);
    }
}
