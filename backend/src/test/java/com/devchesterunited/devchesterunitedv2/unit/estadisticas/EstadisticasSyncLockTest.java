package com.devchesterunited.devchesterunitedv2.unit.estadisticas;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.EstadisticasSyncLock;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EstadisticasSyncLockTest {
    @Test
    void unlockTrueCierraLaConexionSinInvalidarla() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement acquire = statementWithResult(true);
        PreparedStatement release = statementWithResult(true);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(any())).thenReturn(acquire, release);

        Optional<EstadisticasSyncLock.Handle> handle = new EstadisticasSyncLock(dataSource).tryAcquire();
        handle.orElseThrow().close();

        verify(connection).close();
        verify(dataSource, never()).evictConnection(connection);
    }

    @Test
    void unlockFalseInvalidaLaConexionAntesDeDevolverlaAlPool() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement acquire = statementWithResult(true);
        PreparedStatement release = statementWithResult(false);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(any())).thenReturn(acquire, release);

        var handle = new EstadisticasSyncLock(dataSource).tryAcquire().orElseThrow();

        assertThatThrownBy(handle::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("confirmar");
        verify(dataSource).evictConnection(connection);
        verify(connection).close();
    }

    @Test
    void excepcionDuranteUnlockInvalidaLaConexionYProtegeElDoubleClose() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement acquire = statementWithResult(true);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(any())).thenReturn(acquire)
                .thenThrow(new SQLException("unlock failure"));

        var handle = new EstadisticasSyncLock(dataSource).tryAcquire().orElseThrow();

        assertThatThrownBy(handle::close).isInstanceOf(IllegalStateException.class);
        handle.close();
        verify(dataSource).evictConnection(connection);
        verify(connection).close();
    }

    private static PreparedStatement statementWithResult(boolean value) throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getBoolean(1)).thenReturn(value);
        return statement;
    }
}
