package com.devchesterunited.devchesterunitedv2.unit.estadisticas;

import com.devchesterunited.devchesterunitedv2.exception.SincronizacionEnCursoException;
import com.devchesterunited.devchesterunitedv2.service.impl.estadisticas.CatalogoSyncLock;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogoSyncLockTest {
    private static final String LOCK_SQL =
            "SELECT pg_try_advisory_xact_lock(?::integer, ?::integer)";

    @Test
    void adquiereElLockConLaConsultaYLasClavesExistentes() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(LOCK_SQL, Boolean.class, 114644L, 1L)).thenReturn(true);

        new CatalogoSyncLock(jdbc).adquirir();

        verify(jdbc).queryForObject(LOCK_SQL, Boolean.class, 114644L, 1L);
    }

    @Test
    void lockOcupadoConservaLaExcepcionPublica() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(LOCK_SQL, Boolean.class, 114644L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> new CatalogoSyncLock(jdbc).adquirir())
                .isInstanceOf(SincronizacionEnCursoException.class);
    }

    @Test
    void propagaElErrorDeBaseDeDatos() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("database unavailable");
        when(jdbc.queryForObject(eq(LOCK_SQL), eq(Boolean.class), eq(114644L), eq(1L)))
                .thenThrow(failure);

        assertThatThrownBy(() -> new CatalogoSyncLock(jdbc).adquirir())
                .isSameAs(failure);
    }
}
