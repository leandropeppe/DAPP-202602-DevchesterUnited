package com.devchesterunited.devchesterunitedv2.service.impl;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/** Advisory lock de sesión; no mantiene una transacción abierta durante la red. */
@Component
public class EstadisticasSyncLock {
    private static final long LOCK_KEY_1 = 114644L;
    private static final long LOCK_KEY_2 = 2L;
    private final DataSource dataSource;

    public EstadisticasSyncLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<Handle> tryAcquire() {
        try {
            Connection connection = dataSource.getConnection();
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
            return Optional.of(new Handle(connection));
        } catch (SQLException exception) {
            throw new IllegalStateException("No fue posible adquirir el lock estadistico.", exception);
        }
    }

    public static final class Handle implements AutoCloseable {
        private final Connection connection;
        private boolean closed;

        private Handle(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            try (var statement = connection.prepareStatement(
                    "SELECT pg_advisory_unlock(?::integer, ?::integer)")) {
                statement.setLong(1, LOCK_KEY_1);
                statement.setLong(2, LOCK_KEY_2);
                statement.executeQuery().close();
            } catch (SQLException exception) {
                throw new IllegalStateException("No fue posible liberar el lock estadistico.", exception);
            } finally {
                closed = true;
                try {
                    connection.close();
                } catch (SQLException exception) {
                    throw new IllegalStateException("No fue posible cerrar la conexión del lock estadistico.", exception);
                }
            }
        }
    }
}
