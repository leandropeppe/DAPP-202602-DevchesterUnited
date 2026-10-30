package com.devchesterunited.devchesterunitedv2.service.impl.market;

import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Serializa la ejecución completa de inicialización entre instancias de la
 * aplicación. El lock de sesión se mantiene mientras las transacciones de las
 * etapas se confirman, y se libera también si el proceso termina abruptamente.
 */
@Service
public class MarketInitializationExecutionLock {
    private static final long LOCK_KEY_1 = 114644L;
    /** Clave reservada exclusivamente para la inicialización del mercado. */
    private static final long LOCK_KEY_2 = 3L;

    private final DataSource dataSource;

    public MarketInitializationExecutionLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public <T> T ejecutar(Operacion<T> operacion) {
        try (Connection connection = dataSource.getConnection()) {
            adquirir(connection);
            return ejecutarOperacionConLock(connection, operacion);
        } catch (SQLException exception) {
            throw new IllegalStateException("No se pudo adquirir el lock de inicializacion.",
                    exception);
        }
    }

    private static <T> T ejecutarOperacionConLock(Connection connection, Operacion<T> operacion) {
        try {
            T resultado = operacion.ejecutar();
            liberar(connection);
            return resultado;
        } catch (RuntimeException original) {
            liberarTrasFallo(connection, original);
            throw original;
        } catch (SQLException falloLiberacion) {
            throw new IllegalStateException(
                    "No se pudo liberar el lock de inicializacion.", falloLiberacion);
        }
    }

    private static void liberarTrasFallo(Connection connection, RuntimeException original) {
        try {
            liberar(connection);
        } catch (SQLException falloLiberacion) {
            original.addSuppressed(falloLiberacion);
        }
    }

    private static void adquirir(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT pg_advisory_lock(?::integer, ?::integer)")) {
            statement.setLong(1, LOCK_KEY_1);
            statement.setLong(2, LOCK_KEY_2);
            statement.execute();
        }
    }

    private static void liberar(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT pg_advisory_unlock(?::integer, ?::integer)")) {
            statement.setLong(1, LOCK_KEY_1);
            statement.setLong(2, LOCK_KEY_2);
            try (var result = statement.executeQuery()) {
                if (!result.next() || !result.getBoolean(1)) {
                    throw new SQLException("PostgreSQL no confirmó la liberación del lock.");
                }
            }
        }
    }

    @FunctionalInterface
    public interface Operacion<T> {
        T ejecutar();
    }
}
