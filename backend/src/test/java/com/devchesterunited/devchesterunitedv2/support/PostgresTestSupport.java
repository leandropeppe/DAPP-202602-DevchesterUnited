package com.devchesterunited.devchesterunitedv2.support;

import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
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
}
