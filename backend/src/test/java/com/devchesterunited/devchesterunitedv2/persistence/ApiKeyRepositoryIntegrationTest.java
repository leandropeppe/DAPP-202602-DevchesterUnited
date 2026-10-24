package com.devchesterunited.devchesterunitedv2.persistence;
import com.devchesterunited.devchesterunitedv2.persistence.auth.repository.ApiKeyRepository;
import com.devchesterunited.devchesterunitedv2.persistence.auth.repository.UsuarioRepository;

import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.persistence.auth.entity.APIKeyEntity;
import com.devchesterunited.devchesterunitedv2.persistence.auth.entity.UsuarioEntity;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class ApiKeyRepositoryIntegrationTest extends PostgresTestSupport {
    @Autowired ApiKeyRepository repositorio;
    @Autowired UsuarioRepository usuarios;
    @Autowired JdbcTemplate jdbc;
    private final java.util.List<UUID> fixtures = new java.util.ArrayList<>();
    private static final String HASH = "a".repeat(64);
    private static final String HASH2 = "b".repeat(64);

    @Test
    void persisteBuscaYExigeFKDeUsuario() {
        UUID usuario = crearUsuario();
        UUID id = UUID.randomUUID();
        repositorio.saveAndFlush(new APIKeyEntity(id, usuario, HASH, "dev_key_123456", true,
                Instant.now(), null));
        assertThat(repositorio.findById(id)).get().extracting(APIKeyEntity::getId).isEqualTo(id);
        assertThat(repositorio.findByUsuarioIdAndActivaTrue(usuario)).get().extracting(APIKeyEntity::getId).isEqualTo(id);
        assertThat(repositorio.findByHashClave(HASH)).get().extracting(APIKeyEntity::getId).isEqualTo(id);

        UUID inexistente = UUID.randomUUID();
        assertThatThrownBy(() -> insertarJdbc(UUID.randomUUID(), inexistente, HASH2, true, Instant.now(), null))
                .isInstanceOf(SQLException.class).extracting(Throwable::getCause).isNull();
    }

    @Test
    void hashEsUnico() {
        UUID usuario = crearUsuario();
        repositorio.saveAndFlush(new APIKeyEntity(UUID.randomUUID(), usuario, HASH, "dev_key_123456", true, Instant.now(), null));
        assertThatThrownBy(() -> insertarJdbc(UUID.randomUUID(), usuario, HASH, false, Instant.now(), Instant.now()))
                .isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23505"));
    }

    @Test
    void checkActivoImpideRevokedAtYCheckRevocadoExigeFecha() {
        UUID usuario = crearUsuario();
        assertThatThrownBy(() -> insertarJdbc(UUID.randomUUID(), usuario, HASH, true, Instant.now(), Instant.now()))
                .isInstanceOf(SQLException.class).satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
        assertThatThrownBy(() -> insertarJdbc(UUID.randomUUID(), usuario, HASH2, false, Instant.now(), null))
                .isInstanceOf(SQLException.class).satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23514"));
    }

    @Test
    void indiceParcialPermiteUnaActivaPeroNoDosParaElMismoUsuario() throws Exception {
        UUID usuario = crearUsuario();
        insertarJdbc(UUID.randomUUID(), usuario, HASH, true, Instant.now(), null);
        assertThatThrownBy(() -> insertarJdbc(UUID.randomUUID(), usuario, HASH2, true, Instant.now(), null))
                .isInstanceOf(SQLException.class).satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23505"));
        insertarJdbc(UUID.randomUUID(), usuario, HASH2, false, Instant.now(), Instant.now());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM api_keys WHERE user_id = ? AND is_active", Long.class, usuario)).isEqualTo(1L);
    }

    private UUID crearUsuario() {
        UUID id = UUID.randomUUID();
        String nombre = "u" + id.toString().replace("-", "").substring(0, 20);
        usuarios.saveAndFlush(new UsuarioEntity(id, nombre, nombre + "@example.com", "$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW", Instant.now(), Instant.now()));
        fixtures.add(id);
        return id;
    }

    private void insertarJdbc(UUID id, UUID usuario, String hash, boolean activa, Instant creada, Instant revocada) throws Exception {
        inTransaction(connection -> {
            try (var statement = connection.prepareStatement("INSERT INTO api_keys(id,user_id,key_hash,key_prefix,is_active,created_at,revoked_at) VALUES(?,?,?,?,?,?,?)")) {
                statement.setObject(1, id); statement.setObject(2, usuario); statement.setString(3, hash); statement.setString(4, "dev_key_123456"); statement.setBoolean(5, activa);
                statement.setObject(6, java.sql.Timestamp.from(creada));
                statement.setObject(7, revocada == null ? null : java.sql.Timestamp.from(revocada));
                statement.executeUpdate();
            }
            return null;
        });
    }

    @AfterEach void limpiar() { fixtures.forEach(id -> jdbc.update("DELETE FROM usuarios WHERE id = ?", id)); }
}
