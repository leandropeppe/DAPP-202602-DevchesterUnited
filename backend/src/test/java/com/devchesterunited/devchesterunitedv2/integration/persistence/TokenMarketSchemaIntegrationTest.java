package com.devchesterunited.devchesterunitedv2.integration.persistence;


import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.sql.Savepoint;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@SpringBootTest
@Transactional
class TokenMarketSchemaIntegrationTest extends PostgresTestSupport {
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 10, 13, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final String BCRYPT = "$2a$10$7EqJtq98hPqEX7fNZaFWoO5V5f3n0e0D8n8f2h5x0M5K2Qf7tQm2S";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Flyway flyway;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aplicaV19YV20DespuesDeV15YExponeElEsquemaCompleto() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("22");
        assertThat(jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE version IN "
                        + "('15', '19', '20', '21', '22') ORDER BY version",
                String.class)).containsExactly("15", "19", "20", "21", "22");

        List<String> tables = jdbc.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name LIKE 'market_%'
                ORDER BY table_name
                """, String.class);

        assertThat(tables).containsExactlyInAnyOrder(
                "market_confirmed_transactions",
                "market_credit_accounts",
                "market_counterparties",
                "market_holdings",
                "market_idempotency_requests",
                "market_initial_credit_grants",
                "market_movements",
                "market_p2p_offer_cancellations",
                "market_p2p_movements",
                "market_p2p_offers",
                "market_p2p_trade_entries",
                "market_p2p_trades",
                "market_quote_headers",
                "market_quote_versions",
                "market_realized_pnl",
                "market_rejected_attempts",
                "market_state",
                "market_token_issuances");

        limpiarTokenMarketAntesDeVerificarEstadoInicial();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_state", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_counterparties", Integer.class)).isZero();
    }

    @Test
    void conservaTiposUuidYForeignKeysContraUsuariosJugadoresYEstadisticasExistentes() {
        assertThat(jdbc.queryForObject("""
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'usuarios' AND column_name = 'id'
                """, String.class)).isEqualTo("uuid");
        assertThat(jdbc.queryForObject("""
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'jugadores' AND column_name = 'id'
                """, String.class)).isEqualTo("uuid");

        assertThat(foreignKeyCount("market_credit_accounts", "usuarios")).isGreaterThanOrEqualTo(1);
        assertThat(foreignKeyCount("market_holdings", "jugadores")).isEqualTo(1);
        assertThat(foreignKeyCount("market_quote_versions", "jugadores")).isEqualTo(1);
        assertThat(foreignKeyCount("market_token_issuances", "jugadores")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_name IN "
                + "('jugador_estadisticas', 'estadisticas_sync', 'estadisticas_sync_staging')",
                Integer.class)).isEqualTo(3);
    }

    @Test
    void aplicaUnicidadYChecksDeDominio() {
        limpiarTokenMarketAntesDeVerificarEstadoInicial();
        UUID counterpartyId = UUID.randomUUID();
        jdbc.update("INSERT INTO market_counterparties "
                + "(id, counterparty_type, display_name, created_at) VALUES (?, 'SUPERUSER', 'Mercado', ?)",
                counterpartyId, NOW);

        assertConstraintViolation("23505", () -> jdbc.update("INSERT INTO market_counterparties "
                + "(id, counterparty_type, display_name, created_at) VALUES (?, 'SUPERUSER', 'Otro', ?)",
                UUID.randomUUID(), NOW));

        assertConstraintViolation("23514", () -> jdbc.update("INSERT INTO market_credit_accounts "
                + "(id, balance, created_at, updated_at) VALUES (?, 0, ?, ?)",
                UUID.randomUUID(), NOW, NOW));

        UUID playerId = jdbc.queryForObject("SELECT id FROM jugadores ORDER BY id LIMIT 1", UUID.class);
        assertConstraintViolation("23514", () -> jdbc.update("INSERT INTO market_token_issuances "
                + "(player_id, counterparty_id, issued_quantity, inventory_quantity, created_at, updated_at) "
                + "VALUES (?, ?, 99, 99, ?, ?)", playerId, counterpartyId, NOW, NOW));

        assertConstraintViolation("23514", () -> jdbc.update("INSERT INTO market_state "
                + "(id, status, epoch, created_at, updated_at) VALUES (1, 'INVALID', 1, ?, ?)", NOW, NOW));
    }

    @Test
    void aplicaFkCompuestasParaVersionesDeCotizacionYResultadoIdempotente() {
        limpiarTokenMarketAntesDeVerificarEstadoInicial();
        UUID userId = UUID.randomUUID();
        UUID counterpartyId = UUID.randomUUID();
        UUID playerId = jdbc.queryForObject("SELECT id FROM jugadores ORDER BY id LIMIT 1", UUID.class);
        UUID quoteId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        jdbc.update("INSERT INTO usuarios "
                + "(id, username, email, password_hash, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                userId, "markettest" + userId.toString().substring(0, 8),
                userId + "@example.test", BCRYPT, NOW, NOW);
        jdbc.update("INSERT INTO market_counterparties "
                + "(id, counterparty_type, display_name, created_at) VALUES (?, 'SUPERUSER', 'Mercado', ?)",
                counterpartyId, NOW);
        jdbc.update("INSERT INTO market_quote_versions "
                + "(quote_id, version, player_id, price_per_token, published_at, valid_from, validity_status, created_at) "
                + "VALUES (?, 1, ?, 1.00, ?, ?, 'VALID', ?)", quoteId, playerId, NOW, NOW, NOW);
        jdbc.update("INSERT INTO market_idempotency_requests "
                + "(id, user_id, idempotency_key, operation_type, parameters_hash, status, created_at, updated_at) "
                + "VALUES (?, ?, 'key-1', 'PURCHASE', 'hash-1', 'CONFIRMED', ?, ?)",
                requestId, userId, NOW, NOW);
        jdbc.update("INSERT INTO market_confirmed_transactions "
                + "(id, user_id, player_id, player_name_snapshot, operation_type, quantity, unit_price, "
                + "total_amount, counterparty_id, quote_id, quote_version, idempotency_request_id, confirmed_at) "
                + "VALUES (?, ?, ?, 'Jugador snapshot', 'PURCHASE', 1, 1.00, 1.00, ?, ?, 1, ?, ?)",
                UUID.randomUUID(), userId, playerId, counterpartyId, quoteId, requestId, NOW);

        assertConstraintViolation("23505", () -> jdbc.update("INSERT INTO market_idempotency_requests "
                + "(id, user_id, idempotency_key, operation_type, parameters_hash, status, created_at, updated_at) "
                + "VALUES (?, ?, 'key-1', 'PURCHASE', 'hash-2', 'PROCESSING', ?, ?)",
                UUID.randomUUID(), userId, NOW, NOW));
    }

    @Test
    void impideModificarCotizacionesInmutables() {
        UUID playerId = jdbc.queryForObject("SELECT id FROM jugadores ORDER BY id LIMIT 1", UUID.class);
        UUID quoteId = UUID.randomUUID();
        jdbc.update("INSERT INTO market_quote_versions "
                + "(quote_id, version, player_id, price_per_token, published_at, valid_from, validity_status, created_at) "
                + "VALUES (?, 1, ?, 1.00, ?, ?, 'VALID', ?)", quoteId, playerId, NOW, NOW, NOW);

        Throwable failure = executeWithSavepoint(() -> jdbc.update("UPDATE market_quote_versions SET price_per_token = 2.00 "
                + "WHERE quote_id = ? AND version = 1", quoteId));

        assertThat(failure).isInstanceOf(UncategorizedSQLException.class);
        SQLException sqlException = findSqlException(failure);
        assertThat((Object) sqlException).isNotNull();
        assertThat(sqlException.getSQLState()).isEqualTo("P0001");
        assertThat(sqlException.getMessage())
                .contains("Los registros confirmados de Token Market son inmutables");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void flywayPuedeReejecutarseSinCrearNuevasMigraciones() {
        int migrationsBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history", Integer.class);
        flyway.migrate();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("22");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history",
                Integer.class)).isEqualTo(migrationsBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '20'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '21'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '22'",
                Integer.class)).isEqualTo(1);
    }

    private int foreignKeyCount(String tableName, String referencedTableFragment) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.table_constraints tc
                JOIN information_schema.constraint_column_usage ccu
                  ON ccu.constraint_name = tc.constraint_name
                 AND ccu.table_schema = tc.table_schema
                WHERE tc.table_schema = 'public'
                  AND tc.table_name = ?
                  AND tc.constraint_type = 'FOREIGN KEY'
                  AND ccu.table_name LIKE ?
                """, Integer.class, tableName, referencedTableFragment);
    }

    /**
     * Varios tests comparten el contenedor PostgreSQL, pero estos escenarios verifican
     * explícitamente el bootstrap del esquema desde cero. Se limpian solo tablas de
     * Token Market y se respeta el orden de las claves foráneas.
     */
    private void limpiarTokenMarketAntesDeVerificarEstadoInicial() {
        // Las versiones de cotización son inmutables y no admiten DELETE.
        // TRUNCATE es una operación de aislamiento exclusiva de este fixture;
        // no modifica ni desactiva el trigger de V19.
        jdbc.execute("TRUNCATE TABLE market_movements, market_confirmed_transactions, "
                + "market_rejected_attempts, market_idempotency_requests, market_holdings, "
                + "market_realized_pnl, market_quote_headers, market_quote_versions, "
                + "market_initial_credit_grants, market_token_issuances, "
                + "market_credit_accounts, market_counterparties, market_state CASCADE");
    }

    private void assertConstraintViolation(String expectedSqlState, Runnable operation) {
        Throwable failure = executeWithSavepoint(operation);

        assertThat(failure).isInstanceOf(DataIntegrityViolationException.class);
        SQLException sqlException = findSqlException(failure);
        assertThat((Object) sqlException).isNotNull();
        assertThat(sqlException.getSQLState()).isEqualTo(expectedSqlState);
    }

    private Throwable executeWithSavepoint(Runnable operation) {
        ConnectionCallback<Void> callback = connection -> {
            Savepoint savepoint = connection.setSavepoint();
            try {
                operation.run();
                throw new AssertionError("La operación SQL debía fallar");
            } catch (RuntimeException | Error failure) {
                try {
                    connection.rollback(savepoint);
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                } finally {
                    try {
                        connection.releaseSavepoint(savepoint);
                    } catch (SQLException releaseFailure) {
                        failure.addSuppressed(releaseFailure);
                    }
                }
                throw failure;
            }
        };
        return catchThrowable(() -> jdbc.execute(callback));
    }

    private SQLException findSqlException(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException;
            }
            current = current.getCause();
        }
        return null;
    }
}
