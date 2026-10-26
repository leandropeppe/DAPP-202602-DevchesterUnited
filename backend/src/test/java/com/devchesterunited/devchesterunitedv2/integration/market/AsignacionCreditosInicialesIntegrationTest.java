package com.devchesterunited.devchesterunitedv2.integration.market;

import com.devchesterunited.devchesterunitedv2.persistence.auth.entity.UsuarioEntity;
import com.devchesterunited.devchesterunitedv2.persistence.auth.repository.UsuarioRepository;
import com.devchesterunited.devchesterunitedv2.service.impl.market.AsignacionCreditosInicialesService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.auth.AutenticacionService;
import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AsignacionCreditosInicialesIntegrationTest extends PostgresTestSupport {
    private static final Instant FECHA = Instant.parse("2020-10-15T12:00:00Z");
    private static final String HASH = "$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW";

    @Autowired private UsuarioRepository usuarios;
    @Autowired private AsignacionCreditosInicialesService asignaciones;
    @Autowired private AutenticacionService autenticacion;
    @Autowired private JdbcTemplate jdbc;

    private final java.util.Set<UUID> usuariosDePrueba = new java.util.HashSet<>();

    @AfterEach
    void limpiar() {
        for (UUID id : usuariosDePrueba) {
            jdbc.update("DELETE FROM market_initial_credit_grants WHERE user_id = ?", id);
            jdbc.update("DELETE FROM market_credit_accounts WHERE user_id = ?", id);
            jdbc.update("DELETE FROM usuarios WHERE id = ?", id);
        }
    }

    @Test
    void creaCuentaYAsignacionConMilCreditos() {
        UUID id = crearUsuario();

        asignaciones.asignarParaUsuario(id);

        assertThat(saldo(id)).isEqualByComparingTo("1000.00");
        assertThat(cantidadAsignaciones(id)).isEqualTo(1L);
        assertThat(montoAsignacion(id)).isEqualByComparingTo("1000.00");
    }

    @Test
    void sumaElCreditoSinSobrescribirSaldoYLaReejecucionEsIdempotente() {
        UUID id = crearUsuario();
        jdbc.update("INSERT INTO market_credit_accounts"
                + " (id, user_id, balance, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), id, new BigDecimal("250.00"),
                Timestamp.from(FECHA), Timestamp.from(FECHA));

        asignaciones.asignarParaUsuario(id);
        asignaciones.asignarParaUsuario(id);

        assertThat(saldo(id)).isEqualByComparingTo("1250.00");
        assertThat(cantidadAsignaciones(id)).isEqualTo(1L);
    }

    @Test
    void asignacionExistenteConservaElSaldoYNoCreaOtra() {
        UUID id = crearUsuario();
        UUID cuenta = UUID.randomUUID();
        jdbc.update("INSERT INTO market_credit_accounts"
                + " (id, user_id, balance, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                cuenta, id, new BigDecimal("875.25"),
                Timestamp.from(FECHA), Timestamp.from(FECHA));
        jdbc.update("INSERT INTO market_initial_credit_grants"
                + " (id, user_id, credit_account_id, grant_type, amount, created_at)"
                + " VALUES (?, ?, ?, 'INITIAL_GRANT', ?, ?)",
                UUID.randomUUID(), id, cuenta, new BigDecimal("1000.00"), Timestamp.from(FECHA));

        asignaciones.asignarParaUsuario(id);

        assertThat(saldo(id)).isEqualByComparingTo("875.25");
        assertThat(cantidadAsignaciones(id)).isEqualTo(1L);
    }

    @Test
    void dosEjecucionesConcurrentesSoloAcreditanUnaVez() throws Exception {
        UUID id = crearUsuario();
        var inicio = new CountDownLatch(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var primero = executor.submit(() -> ejecutarConcurrenteSinOcultarInterrupcion(id, inicio));
            var segundo = executor.submit(() -> ejecutarConcurrenteSinOcultarInterrupcion(id, inicio));
            primero.get(30, TimeUnit.SECONDS);
            segundo.get(30, TimeUnit.SECONDS);
        }

        assertThat(saldo(id)).isEqualByComparingTo("1000.00");
        assertThat(cantidadAsignaciones(id)).isEqualTo(1L);
    }

    @Test
    void usuarioRegistradoPosteriormenteRecibeElCreditoEnLaMismaOperacion() {
        String sufijo = UUID.randomUUID().toString().replace("-", "");

        var resultado = autenticacion.registrar(
                "t021-" + sufijo, "t021-" + sufijo + "@example.com", "Segura123!");
        usuariosDePrueba.add(resultado.id());

        assertThat(saldo(resultado.id())).isEqualByComparingTo("1000.00");
        assertThat(cantidadAsignaciones(resultado.id())).isEqualTo(1L);
    }

    private void ejecutarConcurrenteSinOcultarInterrupcion(UUID id, CountDownLatch inicio) {
        try {
            ejecutarConcurrente(id, inicio);
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
            throw new AssertionError("La ejecucion concurrente fue interrumpida.", interrupcion);
        }
    }

    private void ejecutarConcurrente(UUID id, CountDownLatch inicio) throws InterruptedException {
        inicio.countDown();
        inicio.await(10, TimeUnit.SECONDS);
        asignaciones.asignarParaUsuario(id);
    }

    private UUID crearUsuario() {
        UUID id = UUID.randomUUID();
        String nombre = "t021-" + id.toString().replace("-", "");
        usuarios.saveAndFlush(new UsuarioEntity(id, nombre, nombre + "@example.com", HASH, FECHA, FECHA));
        usuariosDePrueba.add(id);
        return id;
    }

    private BigDecimal saldo(UUID id) {
        return jdbc.queryForObject(
                "SELECT balance FROM market_credit_accounts WHERE user_id = ?", BigDecimal.class, id);
    }

    private long cantidadAsignaciones(UUID id) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM market_initial_credit_grants WHERE user_id = ?", Long.class, id);
    }

    private BigDecimal montoAsignacion(UUID id) {
        return jdbc.queryForObject(
                "SELECT amount FROM market_initial_credit_grants WHERE user_id = ?", BigDecimal.class, id);
    }
}
