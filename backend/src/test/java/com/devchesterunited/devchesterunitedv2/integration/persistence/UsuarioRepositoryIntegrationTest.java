package com.devchesterunited.devchesterunitedv2.persistence;
import com.devchesterunited.devchesterunitedv2.persistence.auth.repository.UsuarioRepository;

import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.persistence.auth.entity.UsuarioEntity;
import java.time.Instant;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

/** API Java propuesta: constructor completo en español, saveAndFlush y búsquedas Optional.
 * Las restricciones se ejercitan por JDBC para evitar que validaciones Java oculten fallos de BD.
 */
@SpringBootTest
class UsuarioRepositoryIntegrationTest extends PostgresTestSupport {
    @Autowired UsuarioRepository repositorio;
    @Autowired JdbcTemplate jdbc;
    private static final Instant FECHA = Instant.parse("2026-09-01T12:00:00Z");
    private static final String HASH = "$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW";

    @Test
    void persisteYBuscaPorIdNombreYCorreoFueraDelContextoDeEscritura() {
        UUID id = UUID.randomUUID();
        String nombre = nombre(id);
        try {
            repositorio.saveAndFlush(new UsuarioEntity(id, nombre, nombre + "@example.com", HASH, FECHA, FECHA));
            var encontrado = repositorio.findById(id).orElseThrow();
            assertThat(encontrado.getId()).isEqualTo(id);
            assertThat(encontrado.getNombreUsuario()).isEqualTo(nombre);
            assertThat(encontrado.getCorreo()).isEqualTo(nombre + "@example.com");
            assertThat(encontrado.getHashContrasena()).isEqualTo(HASH);
            assertThat(encontrado.getCreadoEn()).isEqualTo(FECHA);
            assertThat(encontrado.getActualizadoEn()).isEqualTo(FECHA);
            assertThat(repositorio.findByNombreUsuario(nombre)).get().extracting(UsuarioEntity::getId).isEqualTo(id);
            assertThat(repositorio.findByCorreo(nombre + "@example.com")).get().extracting(UsuarioEntity::getId).isEqualTo(id);
        } finally { jdbc.update("DELETE FROM usuarios WHERE id = ?", id); }
    }

    @Test
    void busquedasAusentesDevuelvenOptionalVacio() {
        UUID id = UUID.randomUUID();
        assertThat(repositorio.findById(id)).isEmpty();
        assertThat(repositorio.findByNombreUsuario(nombre(id))).isEmpty();
        assertThat(repositorio.findByCorreo(nombre(id) + "@example.com")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void unicidadRealDeNombreYCorreo(boolean mismoNombre) throws Exception {
        UUID id = UUID.randomUUID();
        UUID otro = UUID.randomUUID();
        try {
            insertar(id, nombre(id), nombre(id) + "@example.com", FECHA, FECHA);
            Throwable fallo = catchThrowable(() -> insertar(otro, nombre(mismoNombre ? id : otro),
                    mismoNombre ? nombre(otro) + "@example.com" : nombre(id) + "@example.com", FECHA, FECHA));
            assertThat(fallo).isInstanceOf(SQLException.class);
            assertThat(((SQLException) fallo).getSQLState()).isEqualTo("23505");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE id IN (?, ?)", Long.class, id, otro)).isEqualTo(1L);
        } finally { jdbc.update("DELETE FROM usuarios WHERE id IN (?, ?)", id, otro); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"creacionNula", "actualizacionNula", "anterior"})
    void baseRechazaFechasNulasOIncoherentes(String caso) {
        UUID id = UUID.randomUUID();
        try {
            Throwable fallo = catchThrowable(() -> insertar(id, nombre(id), nombre(id) + "@example.com",
                    caso.equals("creacionNula") ? null : FECHA,
                    caso.equals("actualizacionNula") ? null : FECHA.minusSeconds(1)));
            assertThat(fallo).isInstanceOf(SQLException.class);
            assertThat(((SQLException) fallo).getSQLState()).isEqualTo(caso.equals("anterior") ? "23514" : "23502");
            assertThat(repositorio.findById(id)).isEmpty();
        } finally { jdbc.update("DELETE FROM usuarios WHERE id = ?", id); }
    }

    @Test
    void baseAdmiteActualizacionPosteriorYRechazaRetroceso() throws Exception {
        UUID id = UUID.randomUUID();
        try {
            insertar(id, nombre(id), nombre(id) + "@example.com", FECHA, FECHA.plusSeconds(1));
            Throwable fallo = catchThrowable(() -> inTransaction(connection -> {
                try (var statement = connection.prepareStatement("UPDATE usuarios SET updated_at = ? WHERE id = ?")) {
                    statement.setTimestamp(1, Timestamp.from(FECHA.minusSeconds(1)));
                    statement.setObject(2, id);
                    statement.executeUpdate();
                }
                return null;
            }));
            assertThat(fallo).isInstanceOf(SQLException.class);
            assertThat(((SQLException) fallo).getSQLState()).isEqualTo("23514");
            assertThat(repositorio.findById(id).orElseThrow().getActualizadoEn()).isEqualTo(FECHA.plusSeconds(1));
        } finally { jdbc.update("DELETE FROM usuarios WHERE id = ?", id); }
    }

    private static String nombre(UUID id) { return "u" + id.toString().replace("-", "").substring(0, 20); }

    private void insertar(UUID id, String nombre, String correo, Instant creado, Instant actualizado) throws Exception {
        inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "INSERT INTO usuarios(id, username, email, password_hash, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)")) {
                statement.setObject(1, id);
                statement.setString(2, nombre);
                statement.setString(3, correo);
                statement.setString(4, HASH);
                statement.setTimestamp(5, creado == null ? null : Timestamp.from(creado));
                statement.setTimestamp(6, actualizado == null ? null : Timestamp.from(actualizado));
                statement.executeUpdate();
            }
            return null;
        });
    }
}
