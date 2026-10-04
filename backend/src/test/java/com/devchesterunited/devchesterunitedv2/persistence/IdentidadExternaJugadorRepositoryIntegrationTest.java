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
        insertarJugador(jugador, "Jugador Backfill", 9);
    }

    @Test
    void identidadFootballDataSeAsociaAlJugadorSinColumnaLegacy() {
        insertarIdentidad(identidadFootballData, ProveedorIdentidadExterna.FOOTBALL_DATA, 7010, jugador);

        var identidad = repositorio.findByJugador_IdAndProveedor(jugador,
                ProveedorIdentidadExterna.FOOTBALL_DATA);
        assertThat(identidad).isPresent();
        assertThat(identidad.orElseThrow().getExternalId()).isEqualTo(7010L);
    }

    @Test
    void permiteMismoExternalIdEnProveedoresDiferentes() {
        insertarIdentidad(identidadFootballData, ProveedorIdentidadExterna.FOOTBALL_DATA, 7001, jugador);
        UUID otroJugador = UUID.fromString("50000000-0000-0000-0000-000000000002");
        insertarJugador(otroJugador, "Otro Jugador", 10);
        insertarIdentidad(identidadWhoScored, ProveedorIdentidadExterna.WHOSCORED, 7001, otroJugador);

        assertThat(repositorio.findByProveedorAndExternalId(ProveedorIdentidadExterna.FOOTBALL_DATA, 7001L)).isPresent();
        assertThat(repositorio.findByProveedorAndExternalId(ProveedorIdentidadExterna.WHOSCORED, 7001L)).isPresent();
    }

    @Test
    void imponeUnicidadPorProveedorYExternalId() {
        insertarIdentidad(identidadFootballData, ProveedorIdentidadExterna.FOOTBALL_DATA, 7002, jugador);
        UUID otroJugador = UUID.fromString("50000000-0000-0000-0000-000000000003");
        insertarJugador(otroJugador, "Otro Jugador", 11);

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

        assertThat(jdbc.queryForObject("SELECT id FROM jugadores WHERE id = ?", UUID.class, jugador))
                .isEqualTo(jugador);
    }

    @Test
    void esquemaFinalNoConservaExternalIdLegacyEnJugadores() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'jugadores'
                  AND column_name = 'external_id'
                """, Integer.class)).isZero();
    }

    private void insertarIdentidad(UUID id, ProveedorIdentidadExterna proveedor,
            long externalId, UUID jugadorId) {
        jdbc.update("INSERT INTO identidades_externas_jugador "
                + "(id, jugador_id, proveedor, external_id) VALUES (?, ?, ?, ?)",
                id, jugadorId, proveedor.name(), externalId);
    }

    private void insertarJugador(UUID id, String nombre, int dorsal) {
        jdbc.update("INSERT INTO equipos (id, name, league, is_active, created_at, updated_at) VALUES ((md5('Liga prueba' || '::' || 'Equipo prueba'))::uuid, 'Equipo prueba', 'Liga prueba', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON CONFLICT (id) DO NOTHING");
        jdbc.update("INSERT INTO jugadores (id, name, team_id, position, jersey_number, nationality) VALUES (?, ?, (md5('Liga prueba' || '::' || 'Equipo prueba'))::uuid, 'Forward', ?, 'Argentina')",
                id, nombre, dorsal);
    }
}
