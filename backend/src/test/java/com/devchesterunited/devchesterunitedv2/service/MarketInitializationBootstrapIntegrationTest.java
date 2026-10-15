package com.devchesterunited.devchesterunitedv2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.service.impl.MarketInitializationBootstrap;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class MarketInitializationBootstrapIntegrationTest extends PostgresTestSupport {
    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 10, 14, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final BigDecimal DEFAULT_RESERVE = new BigDecimal("1000000.00");
    private static final BigDecimal CONFIGURED_RESERVE = new BigDecimal("250000.00");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MarketInitializationBootstrap bootstrap;

    @BeforeEach
    @AfterEach
    void limpiarTokenMarket() {
        jdbc.update("DELETE FROM market_movements");
        jdbc.update("DELETE FROM market_confirmed_transactions");
        jdbc.update("DELETE FROM market_rejected_attempts");
        jdbc.update("DELETE FROM market_idempotency_requests");
        jdbc.update("DELETE FROM market_holdings");
        jdbc.update("DELETE FROM market_realized_pnl");
        jdbc.update("DELETE FROM market_quote_headers");
        jdbc.update("DELETE FROM market_quote_versions");
        jdbc.update("DELETE FROM market_initial_credit_grants");
        jdbc.update("DELETE FROM market_token_issuances");
        jdbc.update("DELETE FROM market_credit_accounts");
        jdbc.update("DELETE FROM market_counterparties");
        jdbc.update("DELETE FROM market_state");
    }

    @Test
    void primeraEjecucionPersisteSuperuserCuentaReservaYColumnaInicializada() {
        bootstrap.bootstrap();

        assertThat(count("market_counterparties")).isEqualTo(1);
        assertThat(count("market_credit_accounts")).isEqualTo(1);
        assertThat(decimal("SELECT initial_reserve_initialized FROM market_state"))
                .isEqualByComparingTo(DEFAULT_RESERVE);
        assertThat(decimal("SELECT balance FROM market_credit_accounts"))
                .isEqualByComparingTo(DEFAULT_RESERVE);
        assertThat(epoch()).isEqualTo(1L);
        assertThat(status()).isEqualTo("UNINITIALIZED");
    }

    @Test
    void reejecucionNoDuplicaNiModificaSaldoReservaEpochOEstado() {
        bootstrap.bootstrap();
        BigDecimal balanceAntes = decimal("SELECT balance FROM market_credit_accounts");
        BigDecimal reservaAntes = decimal("SELECT initial_reserve_initialized FROM market_state");
        long epochAntes = epoch();
        String statusAntes = status();

        bootstrap.bootstrap();

        assertThat(count("market_counterparties")).isEqualTo(1);
        assertThat(count("market_credit_accounts")).isEqualTo(1);
        assertThat(decimal("SELECT balance FROM market_credit_accounts"))
                .isEqualByComparingTo(balanceAntes);
        assertThat(decimal("SELECT initial_reserve_initialized FROM market_state"))
                .isEqualByComparingTo(reservaAntes);
        assertThat(epoch()).isEqualTo(epochAntes);
        assertThat(status()).isEqualTo(statusAntes);
    }

    @Test
    void cuentaPreexistenteSinReservaInicializadaFallaYNoDejaEfectos() {
        insertState(null, null, "UNINITIALIZED", 1L);
        insertSuperuser();
        insertCounterpartyAccount(BigDecimal.ZERO);

        assertThatThrownBy(() -> bootstrap.bootstrap())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sin reserva inicializada");

        assertThat(count("market_counterparties")).isEqualTo(1);
        assertThat(count("market_credit_accounts")).isEqualTo(1);
        assertThat(decimal("SELECT balance FROM market_credit_accounts"))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(decimal("SELECT initial_reserve_initialized FROM market_state"))
                .isNull();
        assertThat(epoch()).isEqualTo(1L);
        assertThat(status()).isEqualTo("UNINITIALIZED");
    }

    @Test
    void reservaInicializadaSinCuentaRecreaCuentaSinCambiarReserva() {
        insertState(CONFIGURED_RESERVE, CONFIGURED_RESERVE, "UNINITIALIZED", 5L);
        insertSuperuser();

        bootstrap.bootstrap();

        assertThat(count("market_credit_accounts")).isEqualTo(1);
        assertThat(decimal("SELECT balance FROM market_credit_accounts"))
                .isEqualByComparingTo(CONFIGURED_RESERVE);
        assertThat(decimal("SELECT initial_reserve_initialized FROM market_state"))
                .isEqualByComparingTo(CONFIGURED_RESERVE);
        assertThat(epoch()).isEqualTo(5L);
        assertThat(status()).isEqualTo("UNINITIALIZED");
    }

    @Test
    void utilizaReservaConfiguradaPersistidaDistintaDelValorPredeterminado() {
        insertState(CONFIGURED_RESERVE, null, "UNINITIALIZED", 1L);

        bootstrap.bootstrap();

        assertThat(decimal("SELECT initial_reserve_initialized FROM market_state"))
                .isEqualByComparingTo(CONFIGURED_RESERVE);
        assertThat(decimal("SELECT balance FROM market_credit_accounts"))
                .isEqualByComparingTo(CONFIGURED_RESERVE);
    }

    @Test
    void dosBootstrapsConcurrentesNoCreanDuplicadosNiSobrescribenValores()
            throws Exception {
        CountDownLatch preparados = new CountDownLatch(2);
        CountDownLatch iniciar = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> primero = executor.submit(() -> ejecutarConcurrente(preparados, iniciar));
            Future<?> segundo = executor.submit(() -> ejecutarConcurrente(preparados, iniciar));

            assertThat(preparados.await(10, TimeUnit.SECONDS)).isTrue();
            iniciar.countDown();
            esperar(primero);
            esperar(segundo);
        } finally {
            executor.shutdownNow();
        }

        assertThat(count("market_counterparties")).isEqualTo(1);
        assertThat(count("market_credit_accounts")).isEqualTo(1);
        assertThat(decimal("SELECT balance FROM market_credit_accounts"))
                .isEqualByComparingTo(DEFAULT_RESERVE);
        assertThat(epoch()).isEqualTo(1L);
        assertThat(status()).isEqualTo("UNINITIALIZED");
    }

    private void ejecutarConcurrente(CountDownLatch preparados, CountDownLatch iniciar) {
        preparados.countDown();
        try {
            iniciar.await(10, TimeUnit.SECONDS);
            bootstrap.bootstrap();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("La prueba concurrente fue interrumpida", exception);
        }
    }

    private static void esperar(Future<?> future) throws Exception {
        try {
            future.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            throw new AssertionError("El bootstrap concurrente falló", exception.getCause());
        }
    }

    private void insertState(BigDecimal configured, BigDecimal initialized,
            String status, long epoch) {
        jdbc.update("INSERT INTO market_state "
                        + "(id, status, epoch, initial_reserve_configured, "
                        + "initial_reserve_initialized, created_at, updated_at) "
                        + "VALUES (1, ?, ?, ?, ?, ?, ?)",
                status, epoch, configured, initialized, NOW, NOW);
    }

    private void insertSuperuser() {
        jdbc.update("INSERT INTO market_counterparties "
                        + "(id, counterparty_type, display_name, created_at) "
                        + "VALUES (?, 'SUPERUSER', 'Token Market Superuser', ?)",
                java.util.UUID.randomUUID(), NOW);
    }

    private void insertCounterpartyAccount(BigDecimal balance) {
        String counterpartyId = jdbc.queryForObject(
                "SELECT id::text FROM market_counterparties WHERE counterparty_type = 'SUPERUSER'",
                String.class);
        jdbc.update("INSERT INTO market_credit_accounts "
                        + "(id, user_id, counterparty_id, balance, created_at, updated_at) "
                        + "VALUES (?, NULL, ?::uuid, ?, ?, ?)",
                java.util.UUID.randomUUID(), counterpartyId, balance, NOW, NOW);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private BigDecimal decimal(String query) {
        return jdbc.queryForObject(query, BigDecimal.class);
    }

    private Long epoch() {
        return jdbc.queryForObject("SELECT epoch FROM market_state", Long.class);
    }

    private String status() {
        return jdbc.queryForObject("SELECT status FROM market_state", String.class);
    }
}
