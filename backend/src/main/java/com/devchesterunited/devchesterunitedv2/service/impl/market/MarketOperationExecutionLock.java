package com.devchesterunited.devchesterunitedv2.service.impl.market;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Lock transaccional por solicitud; PostgreSQL lo libera al commit o rollback.
 *
 * T033 debe validar con dos conexiones que: un segundo ejecutor recibe false
 * mientras la primera transaccion esta abierta, el lock queda liberado despues
 * de commit y rollback, una caida deja recuperable la fila PROCESSING y la
 * unicidad usuario/clave no produce movimientos duplicados.
 */
@Component
public class MarketOperationExecutionLock {
    private static final long INITIALIZATION_LOCK_KEY = ((long) 114644 << 32) | 3L;
    private static final long OPERATION_NAMESPACE = 0x4d41524b45544f50L;

    private final JdbcTemplate jdbc;

    public MarketOperationExecutionLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Usa la conexion ligada por Spring a la transaccion actual. */
    public boolean tryAcquire(UUID usuarioId, String claveIdempotencia) {
        long key = deriveKey(usuarioId, claveIdempotencia);
        Boolean acquired = jdbc.queryForObject(
                "SELECT pg_try_advisory_xact_lock(?::bigint)", Boolean.class, key);
        return Boolean.TRUE.equals(acquired);
    }

    /** Espera hasta adquirir el lock en la transaccion actual. */
    public void acquire(UUID usuarioId, String claveIdempotencia) {
        long key = deriveKey(usuarioId, claveIdempotencia);
        jdbc.execute("SELECT pg_advisory_xact_lock(?::bigint)",
                (PreparedStatementCallback<Void>) statement -> {
                    statement.setLong(1, key);
                    statement.execute();
                    return null;
                });
    }

    static long deriveKey(UUID usuarioId, String claveIdempotencia) {
        if (usuarioId == null || claveIdempotencia == null || claveIdempotencia.isBlank()) {
            throw new IllegalArgumentException("La identidad y la clave son obligatorias.");
        }
        String material = "token-market-operation\u0000" + usuarioId
                + "\u0000" + claveIdempotencia.trim();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            long key = ByteBuffer.wrap(digest).getLong() ^ OPERATION_NAMESPACE;
            return key == INITIALIZATION_LOCK_KEY ? key ^ Long.MIN_VALUE : key;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no esta disponible en la JVM.", exception);
        }
    }
}
