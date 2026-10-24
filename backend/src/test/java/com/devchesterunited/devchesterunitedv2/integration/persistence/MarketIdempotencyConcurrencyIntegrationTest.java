package com.devchesterunited.devchesterunitedv2.persistence;
import com.devchesterunited.devchesterunitedv2.persistence.auth.repository.UsuarioRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.persistence.auth.entity.UsuarioEntity;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyStatus;
import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketIdempotencyParameterHasher;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketOperationExecutionLock;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketIdempotencyService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * T033: concurrencia real de la reclamacion, separada de las compras/ventas.
 * Cada hilo usa una transaccion y conexion distintas del pool de Spring.
 */
@SpringBootTest
class MarketIdempotencyConcurrencyIntegrationTest extends PostgresTestSupport {
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    private static final String HASH = "$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW";
    private static final UUID PLAYER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID QUOTE_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MarketOperationExecutionLock operationLock;

    @Autowired
    private MarketIdempotencyService idempotency;

    @Autowired
    private UsuarioRepository usuarios;

    private UUID testUser;

    @AfterEach
    void cleanFixtures() {
        if (testUser != null) {
            jdbc.update("DELETE FROM market_idempotency_requests WHERE user_id = ?", testUser);
            jdbc.update("DELETE FROM usuarios WHERE id = ?", testUser);
            testUser = null;
        }
    }

    @Test
    void soloUnEjecutorAdquiereElLockParaLaMismaSolicitud() throws Exception {
        UUID user = UUID.randomUUID();
        CountDownLatch firstAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> inTransaction(() -> {
                boolean acquired = operationLock.tryAcquire(user, "same-key");
                firstAcquired.countDown();
                await(releaseFirst);
                return acquired;
            }));
            assertThat(firstAcquired.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Boolean> second = executor.submit(() -> inTransaction(
                    () -> operationLock.tryAcquire(user, "same-key")));

            assertThat(second.get(10, TimeUnit.SECONDS)).isFalse();
            releaseFirst.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void elLockSeLiberaAutomaticamenteDespuesDeCommit() throws Exception {
        UUID user = UUID.randomUUID();
        inTransaction(() -> operationLock.tryAcquire(user, "commit-key"));

        assertThat(inTransaction(() -> operationLock.tryAcquire(user, "commit-key"))).isTrue();
    }

    @Test
    void elLockSeLiberaAutomaticamenteDespuesDeRollback() throws Exception {
        UUID user = UUID.randomUUID();

        assertThatThrownBy(() -> inTransaction(() -> {
            assertThat(operationLock.tryAcquire(user, "rollback-key")).isTrue();
            throw new ControlledRollback();
        })).isInstanceOf(ControlledRollback.class);

        assertThat(inTransaction(() -> operationLock.tryAcquire(user, "rollback-key"))).isTrue();
    }

    @Test
    void usuariosYClavesDiferentesNoSeBloqueanEntreSi() throws Exception {
        UUID user = UUID.randomUUID();
        CountDownLatch firstAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> inTransaction(() -> {
                boolean acquired = operationLock.tryAcquire(user, "first-key");
                firstAcquired.countDown();
                await(releaseFirst);
                return acquired;
            }));
            assertThat(firstAcquired.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Boolean> second = executor.submit(() -> inTransaction(
                    () -> operationLock.tryAcquire(user, "second-key")));
            assertThat(second.get(10, TimeUnit.SECONDS)).isTrue();

            releaseFirst.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void processingVencidoReemplazaElTokenYElTokenAnteriorNoConfirma() {
        UUID user = createUser();
        var request = request(user, "expired-key", 1L);
        UUID requestId = UUID.randomUUID();
        UUID oldToken = UUID.randomUUID();
        String hash = MarketIdempotencyParameterHasher.hash(request);
        Instant expired = Instant.now().minus(Duration.ofMinutes(10));
        jdbc.update("INSERT INTO market_idempotency_requests "
                        + "(id, user_id, idempotency_key, operation_type, parameters_hash, status, "
                        + "execution_token, result_reference, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'PURCHASE', ?, 'PROCESSING', ?, NULL, ?, ?)",
                requestId, user, "expired-key", hash, oldToken,
                Timestamp.from(expired), Timestamp.from(expired));

        var reclaimed = idempotency.reclamar(request);

        assertThat(reclaimed.estado()).isEqualTo(MarketIdempotencyStatus.PROCESSING);
        assertThat(reclaimed.solicitudId()).isEqualTo(requestId);
        assertThat(reclaimed.tokenEjecucion()).isNotEqualTo(oldToken);
        assertThatThrownBy(() -> idempotency.confirmar(requestId, oldToken, UUID.randomUUID()))
                .isInstanceOfSatisfying(TokenMarketException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(TokenMarketException.Reason.STALE_REQUEST));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM market_idempotency_requests WHERE user_id = ? AND idempotency_key = ?",
                Long.class, user, "expired-key")).isEqualTo(1L);
    }

    @Test
    void unaSolicitudConcurrenteNoDuplicaLaFilaYPuedeRecuperarseTrasElCommit() throws Exception {
        UUID user = createUser();
        var request = request(user, "race-key", 1L);
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> holder = executor.submit(() -> inTransaction(() -> {
                boolean acquired = operationLock.tryAcquire(user, "race-key");
                lockHeld.countDown();
                await(release);
                return acquired;
            }));
            assertThat(lockHeld.await(10, TimeUnit.SECONDS)).isTrue();

            Future<MarketIdempotencyResult> contender = executor.submit(
                    () -> idempotency.reclamar(request));
            MarketIdempotencyResult pending = contender.get(10, TimeUnit.SECONDS);
            assertThat(pending.estado()).isEqualTo(MarketIdempotencyStatus.PROCESSING);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM market_idempotency_requests WHERE user_id = ? AND idempotency_key = ?",
                    Long.class, user, "race-key")).isZero();

            release.countDown();
            assertThat(holder.get(10, TimeUnit.SECONDS)).isTrue();

            var claimed = idempotency.reclamar(request);
            assertThat(claimed.solicitudId()).isNotNull();
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM market_idempotency_requests WHERE user_id = ? AND idempotency_key = ?",
                    Long.class, user, "race-key")).isEqualTo(1L);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void laRestriccionUnicaImpideDosInsercionesPersistentes() throws Exception {
        UUID user = createUser();
        CountDownLatch firstInserted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondAttempting = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> insertAndHold(user, firstInserted, releaseFirst,
                    UUID.randomUUID()));
            assertThat(firstInserted.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Throwable> second = executor.submit(() -> {
                secondAttempting.countDown();
                try {
                    inTransaction(() -> {
                        insert(user, UUID.randomUUID());
                        return null;
                    });
                    return null;
                } catch (Throwable failure) {
                    return failure;
                }
            });
            assertThat(secondAttempting.await(10, TimeUnit.SECONDS)).isTrue();
            releaseFirst.countDown();
            first.get(10, TimeUnit.SECONDS);

            Throwable failure = second.get(10, TimeUnit.SECONDS);
            assertThat(failure).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM market_idempotency_requests WHERE user_id = ? AND idempotency_key = ?",
                    Long.class, user, "unique-key")).isEqualTo(1L);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    private void insertAndHold(UUID user, CountDownLatch inserted, CountDownLatch release,
            UUID requestId) {
        try {
            inTransaction(() -> {
                insert(user, requestId);
                inserted.countDown();
                await(release);
                return null;
            });
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private void insert(UUID user, UUID requestId) {
        jdbc.update("INSERT INTO market_idempotency_requests "
                        + "(id, user_id, idempotency_key, operation_type, parameters_hash, status, "
                        + "execution_token, result_reference, created_at, updated_at) "
                        + "VALUES (?, ?, 'unique-key', 'PURCHASE', ?, 'PROCESSING', ?, NULL, ?, ?)",
                requestId, user, "a".repeat(64), UUID.randomUUID(),
                Timestamp.from(NOW), Timestamp.from(NOW));
    }

    private UUID createUser() {
        UUID id = UUID.randomUUID();
        String username = "t033-" + id.toString().replace("-", "");
        usuarios.saveAndFlush(new UsuarioEntity(id, username, username + "@example.com", HASH,
                NOW, NOW));
        testUser = id;
        return id;
    }

    private static MarketIdempotencyRequest request(UUID user, String key, long quantity) {
        return new MarketIdempotencyRequest(user, key, "PURCHASE", PLAYER_ID, quantity,
                QUOTE_ID, 1L);
    }

    private <T> T inTransaction(CheckedSupplier<T> work) throws Exception {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            try {
                return work.get();
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new CheckedFailure(exception);
            }
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("La sincronizacion concurrente excedio el limite.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("La prueba concurrente fue interrumpida.", exception);
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    private static final class CheckedFailure extends RuntimeException {
        private CheckedFailure(Exception cause) { super(cause); }
    }

    private static final class ControlledRollback extends RuntimeException { }
}
