package com.devchesterunited.devchesterunitedv2.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.persistence.auth.repository.UsuarioRepository;
import com.devchesterunited.devchesterunitedv2.persistence.auth.entity.UsuarioEntity;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketBalanceService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class MarketBalanceServiceIntegrationTest extends PostgresTestSupport {
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    private static final String HASH = "$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW";

    @Autowired private MarketBalanceService balances;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void prepararFixture() {
        limpiarUsuario();
        usuarios.saveAndFlush(new UsuarioEntity(USER_ID, "market-balance-integration",
                "market-balance-integration@example.com", HASH, NOW, NOW));
        jdbc.update("INSERT INTO market_state (id, status, epoch, created_at, updated_at) "
                + "VALUES (1, 'READY', 17, ?, ?) ON CONFLICT (id) DO UPDATE SET status = 'READY', epoch = 17, "
                + "failure_reason = NULL, updated_at = EXCLUDED.updated_at", Timestamp.from(NOW), Timestamp.from(NOW));
        jdbc.update("INSERT INTO market_credit_accounts "
                + "(id, user_id, balance, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), USER_ID, new BigDecimal("321.45"), Timestamp.from(NOW), Timestamp.from(NOW));
    }

    @AfterEach
    void limpiar() {
        limpiarUsuario();
        jdbc.update("UPDATE market_state SET status = 'UNINITIALIZED', epoch = 1, failure_reason = NULL "
                + "WHERE id = 1");
    }

    @Test
    void leeElSaldoRealDelUsuarioSinCrearRegistros() {
        var resultado = balances.consultar(USER_ID);

        assertThat(resultado.operativa()).isTrue();
        assertThat(resultado.epoch()).isEqualTo(17L);
        assertThat(resultado.saldo()).contains(new BigDecimal("321.45"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM market_credit_accounts WHERE user_id = ?",
                Long.class, USER_ID)).isEqualTo(1L);
    }

    @Test
    void estadoNoReadySePropagaSinConvertirLaConsultaEnOperacion() {
        jdbc.update("UPDATE market_state SET status = 'FAILED', epoch = 18 WHERE id = 1");

        var resultado = balances.consultar(USER_ID);

        assertThat(resultado.estado().name()).isEqualTo("FAILED");
        assertThat(resultado.operativa()).isFalse();
        assertThat(resultado.saldo()).contains(new BigDecimal("321.45"));
    }

    private void limpiarUsuario() {
        jdbc.update("DELETE FROM market_credit_accounts WHERE user_id = ?", USER_ID);
        jdbc.update("DELETE FROM usuarios WHERE id = ?", USER_ID);
    }
}
