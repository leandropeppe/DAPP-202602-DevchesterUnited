package com.devchesterunited.devchesterunitedv2.service.impl.estadisticas;

import com.devchesterunited.devchesterunitedv2.exception.SincronizacionEnCursoException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Adquiere el advisory lock transaccional de la sincronizacion del catalogo. */
@Component
public class CatalogoSyncLock {
    private static final long LOCK_KEY_1 = 114644L;
    private static final long LOCK_KEY_2 = 1L;
    private static final String LOCK_SQL =
            "SELECT pg_try_advisory_xact_lock(?::integer, ?::integer)";

    private final JdbcTemplate jdbc;

    public CatalogoSyncLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void adquirir() {
        Boolean adquirido = jdbc.queryForObject(LOCK_SQL, Boolean.class, LOCK_KEY_1, LOCK_KEY_2);
        if (!Boolean.TRUE.equals(adquirido)) {
            throw new SincronizacionEnCursoException();
        }
    }
}
