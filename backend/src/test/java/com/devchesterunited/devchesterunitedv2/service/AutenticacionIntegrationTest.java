package com.devchesterunited.devchesterunitedv2.service;

import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.service.interfaces.AutenticacionService;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;

/** Contrato propuesto: registrar(nombre, correo, clave) y login(nombre, clave).
 * No hay transacción de test: las llamadas deben terminar con commit propio.
 */
@SpringBootTest
class AutenticacionIntegrationTest extends PostgresTestSupport {
    @Autowired AutenticacionService servicio;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    private static final String CLAVE = "Segura123!";

    private static String identidad() { return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 20); }

    @Test
    void registraNormalizaPersisteBCryptYPermiteLogin() throws Exception {
        String nombre = identidad();
        try {
            servicio.registrar(" " + nombre.toUpperCase(java.util.Locale.ROOT) + " ",
                    " " + nombre.toUpperCase(java.util.Locale.ROOT) + "@EXAMPLE.COM ", CLAVE);
            var fila = jdbc.queryForMap("SELECT * FROM usuarios WHERE username = ?", nombre);
            assertThat(fila).containsEntry("email", nombre + "@example.com");
            String hash = (String) fila.get("password_hash");
            assertThat(hash).startsWith("$2").isNotEqualTo(CLAVE);
            assertThat(encoder.matches(CLAVE, hash)).isTrue();
            // Respuesta propuesta con getAccessToken(): JWT vinculado al usuario persistido.
            var respuesta = servicio.login(" " + nombre.toUpperCase(java.util.Locale.ROOT) + " ", CLAVE);
            var token = com.nimbusds.jwt.SignedJWT.parse(respuesta.tokenAcceso());
            assertThat(token.getHeader().getAlgorithm().getName()).isEqualTo("HS256");
            var claims = token.getJWTClaimsSet();
            assertThat(claims.getSubject()).isEqualTo("usr_" + fila.get("id"));
            assertThat(claims.getIssuer()).isEqualTo("devchester-united");
            assertThat(claims.getAudience()).contains("devchester-api");
            assertThat(Duration.between(claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant()))
                    .isEqualTo(Duration.ofHours(1));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE username = ?", Long.class, nombre)).isEqualTo(1L);
        } finally { limpiar(nombre); }
    }

    @Test
    void credencialesErroneasSonGenericasYNoTransformanContrasena() {
        String nombre = identidad();
        try {
            servicio.registrar(nombre, nombre + "@example.com", CLAVE);
            Throwable incorrecta = catchThrowable(() -> servicio.login(nombre, "incorrecta"));
            Throwable ausente = catchThrowable(() -> servicio.login(identidad(), "incorrecta"));
            assertThat(incorrecta).isInstanceOf(BadCredentialsException.class);
            assertThat(ausente).isInstanceOf(BadCredentialsException.class).hasMessage(incorrecta.getMessage());
            assertThatThrownBy(() -> servicio.login(nombre, " " + CLAVE)).isInstanceOf(BadCredentialsException.class);
        } finally { limpiar(nombre); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void duplicadosConcurrentesConflictoSinDobleInsercion(boolean mismoNombre) throws Exception {
        String primero = identidad();
        String segundo = identidad();
        String correo = primero + "@example.com";
        var barrera = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> competir(barrera, primero, correo));
            var b = executor.submit(() -> competir(barrera, mismoNombre ? primero.toUpperCase(java.util.Locale.ROOT) : segundo,
                    mismoNombre ? segundo + "@example.com" : correo.toUpperCase(java.util.Locale.ROOT)));
            var resultados = java.util.Arrays.asList(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
            assertThat(resultados.stream().filter(java.util.Objects::isNull).count()).isEqualTo(1);
            assertThat(resultados.stream().filter(java.util.Objects::nonNull).toList())
                    .singleElement().isInstanceOf(DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE username IN (?, ?) OR email IN (?, ?)",
                    Long.class, primero, segundo, correo, segundo + "@example.com")).isEqualTo(1L);
        } finally { limpiar(primero); limpiar(segundo); }
    }

    private Throwable competir(CyclicBarrier barrera, String nombre, String correo) throws Exception {
        awaitBarrier(barrera, Duration.ofSeconds(10));
        return catchThrowable(() -> servicio.registrar(nombre, correo, CLAVE));
    }

    private void limpiar(String nombre) {
        jdbc.update("DELETE FROM market_initial_credit_grants WHERE user_id IN "
                + "(SELECT id FROM usuarios WHERE username = ? OR email = ?)",
                nombre, nombre + "@example.com");
        jdbc.update("DELETE FROM market_credit_accounts WHERE user_id IN "
                + "(SELECT id FROM usuarios WHERE username = ? OR email = ?)",
                nombre, nombre + "@example.com");
        jdbc.update("DELETE FROM usuarios WHERE username = ? OR email = ?", nombre, nombre + "@example.com");
    }
}
