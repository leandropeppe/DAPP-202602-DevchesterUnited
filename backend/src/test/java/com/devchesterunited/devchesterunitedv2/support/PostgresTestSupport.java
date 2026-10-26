package com.devchesterunited.devchesterunitedv2.support;

import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

/**
 * PostgreSQL compartido durante la JVM de pruebas; Ryuk libera el contenedor al terminar.
 * Cada suite debe aislar sus fixtures y limpiarlos; no cerrar el contenedor entre suites
 * porque los contextos Spring pueden permanecer en cache. No habilita paralelismo de fixtures.
 */
public abstract class PostgresTestSupport {
    private static class Database {
        private static final PostgreSQLContainer<?> INSTANCE = start();

        private static PostgreSQLContainer<?> start() {
            var container = new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("devchester_test")
                    .withUsername("test")
                    .withPassword("test");
            container.start();
            return container;
        }
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        var database = Database.INSTANCE;
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    /** Conexion nueva, ajena a la transaccion de Spring del hilo que llama. Cerrar con try-with-resources. */
    protected static Connection openConnection() throws SQLException {
        var database = Database.INSTANCE;
        return DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());
    }

    @FunctionalInterface
    protected interface TransactionWork<T> {
        T execute(Connection connection) throws Exception;
    }

    /** Commit al finalizar; rollback ante fallo, sin reintentos y con conexion independiente. */
    protected static <T> T inTransaction(TransactionWork<T> work) throws Exception {
        try (var connection = openConnection()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (Exception | Error failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        }
    }

    /** Scripts SQL de fixtures controlados por la suite, aplicados atomicamente. */
    protected static void executeFixtures(Resource... scripts) throws Exception {
        inTransaction(connection -> {
            for (Resource script : scripts) {
                ScriptUtils.executeSqlScript(connection, script);
            }
            return null;
        });
    }

    protected static void awaitBarrier(CyclicBarrier barrier, Duration timeout) throws Exception {
        barrier.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Elimina el catalogo de jugadores y las filas de fixtures que lo referencian.
     *
     * Algunas tablas del mercado son deliberadamente inmutables en produccion. En
     * tests, las filas que se eliminan aqui pertenecen a fixtures y los triggers se
     * deshabilitan unicamente durante esta limpieza controlada.
     */
    protected static void limpiarJugadoresYDependencias(JdbcTemplate jdbc, UUID... playerIds) {
        for (UUID playerId : playerIds) {
            limpiarJugadorYDependencias(jdbc, playerId);
        }
    }

    /** Limpia sólo jugadores que tienen residuos reconocibles de fixtures de mercado. */
    protected static void limpiarFixturesDeMercado(JdbcTemplate jdbc) {
        List<UUID> playerIds = jdbc.query("""
                SELECT player_id FROM market_token_issuances
                UNION SELECT player_id FROM market_quote_headers
                UNION SELECT player_id FROM market_quote_versions
                UNION SELECT player_id FROM market_holdings
                UNION SELECT player_id FROM market_confirmed_transactions
                UNION SELECT player_id FROM market_rejected_attempts
                UNION SELECT player_id FROM market_movements WHERE player_id IS NOT NULL
                """, (rs, row) -> rs.getObject(1, UUID.class));
        limpiarJugadoresYDependencias(jdbc, playerIds.toArray(UUID[]::new));
    }

    /** Limpieza del catálogo completo para suites cuyo contrato exige exactamente su fixture. */
    protected static void limpiarTodosLosJugadoresYDependencias(JdbcTemplate jdbc) {
        List<UUID> playerIds = jdbc.query("SELECT id FROM jugadores",
                (rs, row) -> rs.getObject(1, UUID.class));
        limpiarJugadoresYDependencias(jdbc, playerIds.toArray(UUID[]::new));
    }

    private static void limpiarJugadorYDependencias(JdbcTemplate jdbc, UUID playerId) {
        jdbc.update("DELETE FROM market_movements WHERE player_id = ?", playerId);
        jdbc.update("DELETE FROM market_movements WHERE transaction_id IN "
                + "(SELECT id FROM market_confirmed_transactions WHERE player_id = ?)", playerId);
        jdbc.update("DELETE FROM market_holdings WHERE player_id = ?", playerId);
        jdbc.update("DELETE FROM market_rejected_attempts WHERE player_id = ?", playerId);
        jdbc.update("DELETE FROM market_token_issuances WHERE player_id = ?", playerId);
        jdbc.update("DELETE FROM market_quote_headers WHERE player_id = ?", playerId);
        jdbc.update("DELETE FROM cotizacion_actual_estrategia WHERE jugador_id = ?", playerId);
        jdbc.update("DELETE FROM historico_cotizacion_estrategia WHERE jugador_id = ?", playerId);
        jdbc.update("DELETE FROM resultado_elegibilidad_valorizacion WHERE jugador_id = ?", playerId);
        jdbc.update("DELETE FROM jugador_estadisticas WHERE jugador_id = ?", playerId);
        jdbc.update("DELETE FROM estadisticas_sync_staging WHERE jugador_id = ?", playerId);
        jdbc.update("DELETE FROM identidades_externas_jugador WHERE jugador_id = ?", playerId);
        jdbc.update("ALTER TABLE market_confirmed_transactions DISABLE TRIGGER trg_market_confirmed_transactions_immutable");
        try {
            jdbc.update("DELETE FROM market_confirmed_transactions WHERE player_id = ?", playerId);
        } finally {
            jdbc.update("ALTER TABLE market_confirmed_transactions ENABLE TRIGGER trg_market_confirmed_transactions_immutable");
        }
        jdbc.update("ALTER TABLE market_quote_versions DISABLE TRIGGER trg_market_quote_versions_immutable");
        try {
            jdbc.update("DELETE FROM market_quote_versions WHERE player_id = ?", playerId);
        } finally {
            jdbc.update("ALTER TABLE market_quote_versions ENABLE TRIGGER trg_market_quote_versions_immutable");
        }
        jdbc.update("DELETE FROM jugadores WHERE id = ?", playerId);
    }

    /** Elimina un usuario de fixture junto con sus filas de mercado dependientes. */
    protected static void limpiarUsuarioYDependencias(JdbcTemplate jdbc, java.util.UUID userId) {
        jdbc.update("DELETE FROM market_movements WHERE token_user_id = ?", userId);
        jdbc.update("DELETE FROM market_movements WHERE transaction_id IN "
                + "(SELECT id FROM market_confirmed_transactions WHERE user_id = ?)", userId);
        jdbc.update("DELETE FROM market_holdings WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM market_realized_pnl WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM market_initial_credit_grants WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM market_rejected_attempts WHERE user_id = ?", userId);
        jdbc.update("ALTER TABLE market_confirmed_transactions DISABLE TRIGGER trg_market_confirmed_transactions_immutable");
        try {
            jdbc.update("DELETE FROM market_confirmed_transactions WHERE user_id = ?", userId);
        } finally {
            jdbc.update("ALTER TABLE market_confirmed_transactions ENABLE TRIGGER trg_market_confirmed_transactions_immutable");
        }
        jdbc.update("DELETE FROM market_idempotency_requests WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM market_credit_accounts WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM usuarios WHERE id = ?", userId);
    }
}
