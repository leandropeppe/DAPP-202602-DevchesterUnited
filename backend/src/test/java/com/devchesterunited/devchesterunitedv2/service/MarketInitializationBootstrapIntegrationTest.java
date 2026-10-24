package com.devchesterunited.devchesterunitedv2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketInitializationBootstrap;
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

    @Test
    void publicaCotizacionInicialSoloParaActivosYLaReejecucionEsIdempotente() {
        var jugadorActivo = insertarJugador(true);
        var jugadorInactivo = insertarJugador(false);

        bootstrap.bootstrap();

        assertThat(count("market_token_issuances", jugadorActivo)).isEqualTo(1);
        assertThat(longValue("SELECT issued_quantity FROM market_token_issuances "
                + "WHERE player_id = ?", jugadorActivo)).isEqualTo(100L);
        assertThat(count("market_quote_versions", jugadorActivo)).isEqualTo(1);
        assertThat(decimal("SELECT price_per_token FROM market_quote_versions "
                + "WHERE player_id = ?", jugadorActivo)).isEqualByComparingTo("1.00");
        assertThat(count("market_quote_headers", jugadorActivo)).isEqualTo(1);
        assertThat(count("market_token_issuances", jugadorInactivo)).isZero();
        assertThat(count("market_quote_versions", jugadorInactivo)).isZero();

        bootstrap.bootstrap();

        assertThat(count("market_token_issuances", jugadorActivo)).isEqualTo(1);
        assertThat(count("market_quote_versions", jugadorActivo)).isEqualTo(1);
        assertThat(longValue("SELECT current_version FROM market_quote_headers "
                + "WHERE player_id = ?", jugadorActivo)).isEqualTo(1L);
    }

    @Test
    void fallaAtomicamenteSiUnaCotizacionInicialPersistidaEsInvalida() {
        var jugador = insertarJugador(true);
        insertarCotizacionInicialInvalida(jugador);

        assertThatThrownBy(() -> bootstrap.bootstrap())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inicial persistida");

        assertThat(count("market_token_issuances", jugador)).isZero();
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

    private long count(String table, java.util.UUID playerId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table
                + " WHERE player_id = ?", Long.class, playerId);
    }

    private BigDecimal decimal(String query) {
        return jdbc.queryForObject(query, BigDecimal.class);
    }

    private BigDecimal decimal(String query, java.util.UUID playerId) {
        return jdbc.queryForObject(query, BigDecimal.class, playerId);
    }

    private long longValue(String query, java.util.UUID playerId) {
        return jdbc.queryForObject(query, Long.class, playerId);
    }

    private java.util.UUID insertarJugador(boolean activo) {
        java.util.UUID jugadorId = java.util.UUID.randomUUID();
        java.util.UUID equipoId = jdbc.queryForObject(
                "SELECT id FROM equipos LIMIT 1", java.util.UUID.class);
        jdbc.update("INSERT INTO jugadores "
                        + "(id, name, position, jersey_number, nationality, team_id, "
                        + "is_active, created_at, updated_at) "
                        + "VALUES (?, ?, 'Forward', 9, 'Argentina', ?, ?, ?, ?)",
                jugadorId, "T023 Player " + jugadorId, equipoId, activo, NOW, NOW);
        return jugadorId;
    }

    private void insertarCotizacionInicialInvalida(java.util.UUID jugadorId) {
        java.util.UUID quoteId = java.util.UUID.randomUUID();
        jdbc.update("INSERT INTO market_quote_versions "
                        + "(quote_id, version, player_id, price_per_token, published_at, "
                        + "valid_from, valid_until, validity_status, strategy_reference, created_at) "
                        + "VALUES (?, 1, ?, 2.00, ?, ?, NULL, 'VALID', NULL, ?)",
                quoteId, jugadorId, NOW, NOW, NOW);
        jdbc.update("INSERT INTO market_quote_headers "
                        + "(player_id, quote_id, current_version, updated_at) VALUES (?, ?, 1, ?)",
                jugadorId, quoteId, NOW);
    }

    private Long epoch() {
        return jdbc.queryForObject("SELECT epoch FROM market_state", Long.class);
    }

    private String status() {
        return jdbc.queryForObject("SELECT status FROM market_state", String.class);
    }
}
