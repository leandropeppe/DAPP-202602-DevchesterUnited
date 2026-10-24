package com.devchesterunited.devchesterunitedv2.service;
import com.devchesterunited.devchesterunitedv2.persistence.auth.entity.UsuarioEntity;

import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.persistence.auth.repository.UsuarioRepository;
import com.devchesterunited.devchesterunitedv2.service.interfaces.auth.ApiKeyService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** Pruebas del caso de uso contra PostgreSQL real; no sustituyen el almacenamiento por mocks. */
@SpringBootTest
class ApiKeyIntegrationTest extends PostgresTestSupport {
    @Autowired ApiKeyService servicio;
    @Autowired UsuarioRepository usuarios;
    @Autowired JdbcTemplate jdbc;
    private final java.util.List<UUID> fixtures = new java.util.ArrayList<>();

    @Test
    void generaClavePersistiendoSoloHashPrefijoYUnaActiva() {
        UUID usuario = crearUsuario();
        var respuesta = servicio.generar(usuario);
        String secreto = respuesta.apiKey();
        assertThat(secreto).matches("dev_key_[A-Za-z0-9_-]{43}");
        var fila = jdbc.queryForMap("SELECT * FROM api_keys WHERE user_id = ?", usuario);
        assertThat(fila.get("key_hash")).isNotEqualTo(secreto).satisfiesAnyOf(
                valor -> assertThat(valor.toString()).matches("[0-9a-f]{64}"));
        assertThat(fila)
                .containsEntry("key_prefix", secreto.substring(0, 14))
                .containsEntry("is_active", true);
        assertThat(fila.get("revoked_at")).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM api_keys WHERE user_id = ? AND is_active", Long.class, usuario))
                .isEqualTo(1L);
        assertThat(respuesta.toString()).doesNotContain(secreto);
    }

    @Test
    void rotacionRevocaLaAnteriorYConservaSoloLaNueva() {
        UUID usuario = crearUsuario();
        String primera = servicio.generar(usuario).apiKey();
        String segunda = servicio.generar(usuario).apiKey();
        assertThat(segunda).isNotEqualTo(primera);
        var filas = jdbc.queryForList("SELECT key_hash, key_prefix, is_active, created_at, revoked_at FROM api_keys WHERE user_id = ? ORDER BY created_at", usuario);
        assertThat(filas).hasSize(2);
        assertThat(filas.get(0)).containsEntry("is_active", false);
        assertThat(filas.get(0).get("revoked_at")).isNotNull();
        assertThat(filas.get(1)).containsEntry("is_active", true);
        assertThat(filas.get(1).get("revoked_at")).isNull();
        assertThat(filas.get(0)).containsEntry("key_prefix", primera.substring(0, 14));
        assertThat(filas.get(1)).containsEntry("key_prefix", segunda.substring(0, 14));
    }

    @Test
    void falloDeInsercionHaceRollbackYConservaClaveAnterior() throws Exception {
        UUID usuario = crearUsuario();
        String primera = servicio.generar(usuario).apiKey();
        inTransaction(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE OR REPLACE FUNCTION test_api_key_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'fallo controlado'; END; $$");
                statement.execute("CREATE TRIGGER test_api_key_failure_trigger BEFORE INSERT ON api_keys FOR EACH ROW EXECUTE FUNCTION test_api_key_failure()");
            }
            return null;
        });
        try {
            assertThatThrownBy(() -> servicio.generar(usuario)).isInstanceOf(RuntimeException.class);
            var fila = jdbc.queryForMap("SELECT key_prefix, is_active, revoked_at FROM api_keys WHERE user_id = ? AND is_active", usuario);
            assertThat(fila).containsEntry("key_prefix", primera.substring(0, 14));
            assertThat(fila).containsEntry("is_active", true);
            assertThat(fila.get("revoked_at")).isNull();
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS test_api_key_failure_trigger ON api_keys");
            jdbc.execute("DROP FUNCTION IF EXISTS test_api_key_failure()");
        }
    }

    private UUID crearUsuario() {
        UUID id = UUID.randomUUID();
        String nombre = "u" + id.toString().replace("-", "").substring(0, 20);
        usuarios.saveAndFlush(new com.devchesterunited.devchesterunitedv2.persistence.auth.entity.UsuarioEntity(
                id, nombre, nombre + "@example.com", "$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW", Instant.now(), Instant.now()));
        fixtures.add(id);
        return id;
    }

    @AfterEach
    void limpiar() { fixtures.forEach(id -> jdbc.update("DELETE FROM usuarios WHERE id = ?", id)); }
}
