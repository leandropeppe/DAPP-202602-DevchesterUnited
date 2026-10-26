package com.devchesterunited.devchesterunitedv2.service.impl.estadisticas;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/**
 * Advisory lock de sesión para serializar sincronizaciones.
 *
 * PostgreSQL asocia el lock a la sesión, por lo que el {@link Handle} conserva
 * la misma {@link Connection} para liberarlo. Devolverla al pool antes de
 * {@code close()} rompería la exclusión y dejaría la siguiente sincronización
 * expuesta a concurrencia indebida.
 */
@Component
public class EstadisticasSyncLock {
    private static final long LOCK_KEY_1 = 114644L;
    private static final long LOCK_KEY_2 = 2L;
    private final DataSource dataSource;

    public EstadisticasSyncLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<Handle> tryAcquire() {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            boolean acquired;
            try (var statement = connection.prepareStatement(
                    "SELECT pg_try_advisory_lock(?::integer, ?::integer)")) {
                statement.setLong(1, LOCK_KEY_1);
                statement.setLong(2, LOCK_KEY_2);
                try (var result = statement.executeQuery()) {
                    result.next();
                    acquired = result.getBoolean(1);
                }
            }
            if (!acquired) {
                connection.close();
                return Optional.empty();
            }
            return Optional.of(new Handle(connection, dataSource));
        } catch (SQLException exception) {
            closeAfterAcquireFailure(connection);
            throw new IllegalStateException("No fue posible adquirir el lock estadistico.", exception);
        }
    }

    private static void closeAfterAcquireFailure(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // No ocultar el error original de adquisición.
            }
        }
    }

    public static final class Handle implements AutoCloseable {
        private final Connection connection;
        private final DataSource dataSource;
        private boolean closed;

        private Handle(Connection connection, DataSource dataSource) {
            this.connection = connection;
            this.dataSource = dataSource;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            IllegalStateException failure = null;
            boolean unlockConfirmed = false;
            try (var statement = connection.prepareStatement(
                    "SELECT pg_advisory_unlock(?::integer, ?::integer)")) {
                statement.setLong(1, LOCK_KEY_1);
                statement.setLong(2, LOCK_KEY_2);
                try (var result = statement.executeQuery()) {
                    result.next();
                    unlockConfirmed = result.getBoolean(1);
                }
                if (!unlockConfirmed) {
                    failure = new IllegalStateException(
                            "No fue posible confirmar la liberación del lock estadistico.");
                }
            } catch (SQLException exception) {
                failure = new IllegalStateException("No fue posible liberar el lock estadistico.", exception);
            } finally {
                if (!unlockConfirmed) {
                    invalidarConexion(failure);
                } else {
                    failure = closeConnection(failure);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }

        private void invalidarConexion(IllegalStateException failure) {
            try {
                if (dataSource instanceof HikariDataSource hikari) {
                    hikari.evictConnection(connection);
                } else {
                    connection.abort(Runnable::run);
                }
            } catch (RuntimeException | SQLException invalidationFailure) {
                if (failure != null) {
                    failure.addSuppressed(invalidationFailure);
                }
            }
            closeConnection(failure);
        }

        private IllegalStateException closeConnection(IllegalStateException failure) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                if (failure != null) {
                    failure.addSuppressed(closeFailure);
                } else {
                    failure = new IllegalStateException(
                            "No fue posible cerrar la conexion del lock estadistico.", closeFailure);
                }
            }
            return failure;
        }
    }
}
