package com.devchesterunited.devchesterunitedv2.integration.persistence;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPurchaseRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPurchaseResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteInvalidationRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteInvalidationResult;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketPurchaseService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.QuotePublicationPort;
import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T044: concurrencia real del orquestador de compras contra PostgreSQL.
 *
 * <p>La clase no es transaccional a nivel de test: los fixtures se confirman
 * antes de liberar los workers y cada invocacion del servicio abre su propia
 * transaccion Spring. Los locks, los conflictos de unicidad y el commit atomico
 * se verifican leyendo el estado durable desde JdbcTemplate.</p>
 */
@SpringBootTest(properties = "token-market.initialization.auto-start=false")
class MarketPurchaseConcurrencyIntegrationTest extends PostgresTestSupport {
    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 10, 15, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final String PASSWORD_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoO5V5f3n0e0D8n8f2h5x0M5K2Qf7tQm2S";
    private static final Duration START_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration COMPLETION_TIMEOUT = Duration.ofSeconds(30);

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MarketPurchaseService purchases;
    @Autowired private QuotePublicationPort quotes;

    private final List<ExecutorService> executors = new ArrayList<>();
    private final List<Fixture> fixtures = new ArrayList<>();

    @AfterEach
    void stopWorkers() {
        executors.forEach(executor -> {
            executor.shutdownNow();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        executors.clear();
        fixtures.forEach(this::cleanupFixture);
        fixtures.clear();
    }

    @Test
    void dosUsuariosCompitenPorUnaSolaUnidadDeInventario() throws Exception {
        Fixture fixture = fixture(2, new BigDecimal("100.00"), 1);
        List<CallOutcome> outcomes = concurrently(
                () -> purchase(fixture.request(fixture.userOne, "inventory-one", 1)),
                () -> purchase(fixture.request(fixture.userTwo, "inventory-two", 1)));

        assertThat(outcomes).hasSize(2);
        assertThat(outcomes.stream().filter(this::confirmed).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(this::failed).count()).isLessThanOrEqualTo(1);
        assertThat(longValue("SELECT inventory_quantity FROM market_token_issuances "
                + "WHERE player_id = ?", fixture.playerId)).isZero();
        assertThat(longValue("SELECT COALESCE(SUM(quantity), 0) FROM market_confirmed_transactions "
                + "WHERE player_id = ? AND operation_type = 'PURCHASE'", fixture.playerId))
                .isEqualTo(1L);
        assertThat(longValue("SELECT COALESCE(SUM(quantity), 0) FROM market_holdings "
                + "WHERE player_id = ?", fixture.playerId)).isEqualTo(1L);
        assertThat(longValue("SELECT COUNT(*) FROM market_movements m "
                + "JOIN market_confirmed_transactions t ON t.id = m.transaction_id "
                + "WHERE t.player_id = ?", fixture.playerId)).isEqualTo(4L);
    }

    @Test
    void dosComprasDelMismoUsuarioNoPuedenGastarDosVecesElSaldo() throws Exception {
        Fixture fixture = fixture(1, new BigDecimal("1.25"), 100);
        List<CallOutcome> outcomes = concurrently(
                () -> purchase(fixture.request(fixture.userOne, "balance-one", 1)),
                () -> purchase(fixture.request(fixture.userOne, "balance-two", 1)));

        assertThat(outcomes.stream().filter(this::confirmed).count()).isEqualTo(1);
        assertThat(decimalValue("SELECT balance FROM market_credit_accounts "
                + "WHERE user_id = ?", fixture.userOne)).isEqualByComparingTo("0.00");
        assertThat(longValue("SELECT COUNT(*) FROM market_confirmed_transactions "
                + "WHERE user_id = ?", fixture.userOne)).isEqualTo(1L);
        assertThat(longValue("SELECT COALESCE(SUM(quantity), 0) FROM market_holdings "
                + "WHERE user_id = ? AND player_id = ?", fixture.userOne, fixture.playerId))
                .isEqualTo(1L);
        assertThat(longValue("SELECT COUNT(*) FROM market_movements m "
                + "JOIN market_confirmed_transactions t ON t.id = m.transaction_id "
                + "WHERE t.user_id = ?", fixture.userOne)).isEqualTo(4L);
    }

    @Test
    void mismaClaveYParametrosSoloProduceUnaCompra() throws Exception {
        Fixture fixture = fixture(1, new BigDecimal("100.00"), 100);
        String key = "same-key";
        List<CallOutcome> outcomes = concurrently(
                () -> purchase(fixture.request(fixture.userOne, key, 2)),
                () -> purchase(fixture.request(fixture.userOne, key, 2)));

        assertThat(outcomes.stream().filter(this::confirmed).count()).isEqualTo(1);
        assertThat(outcomes.stream().allMatch(this::isAllowedIdempotentOutcome)).isTrue();
        assertThat(stringValue("SELECT status FROM market_idempotency_requests "
                + "WHERE user_id = ? AND idempotency_key = ?", fixture.userOne, key))
                .isEqualTo("CONFIRMED");
        assertThat(longValue("SELECT COUNT(*) FROM market_idempotency_requests "
                + "WHERE user_id = ? AND idempotency_key = ?", fixture.userOne, key)).isEqualTo(1L);
        assertThat(longValue("SELECT COUNT(*) FROM market_confirmed_transactions "
                + "WHERE user_id = ?", fixture.userOne)).isLessThanOrEqualTo(1L);
        assertThat(longValue("SELECT COUNT(*) FROM market_movements m "
                + "JOIN market_confirmed_transactions t ON t.id = m.transaction_id "
                + "WHERE t.user_id = ?", fixture.userOne)).isEqualTo(
                longValue("SELECT COUNT(*) FROM market_confirmed_transactions WHERE user_id = ?",
                        fixture.userOne) * 4L);
        assertThat(longValue("SELECT COALESCE(SUM(quantity), 0) FROM market_holdings "
                + "WHERE user_id = ? AND player_id = ?", fixture.userOne, fixture.playerId))
                .isEqualTo(2L);
    }

    @Test
    void mismaClaveConParametrosDistintosConservaUnSoloResultado() throws Exception {
        Fixture fixture = fixture(1, new BigDecimal("100.00"), 100);
        String key = "conflicting-key";
        List<CallOutcome> outcomes = concurrently(
                () -> purchase(fixture.request(fixture.userOne, key, 1)),
                () -> purchase(fixture.request(fixture.userOne, key, 2)));

        assertThat(outcomes.stream().filter(this::confirmed).count()).isEqualTo(1);
        long confirmedQuantity = longValue("SELECT COALESCE(MAX(quantity), 0) "
                + "FROM market_confirmed_transactions WHERE user_id = ?", fixture.userOne);
        assertThat(confirmedQuantity).isIn(1L, 2L);
        long divergentQuantity = confirmedQuantity == 1 ? 2 : 1;
        CallOutcome conflict = purchase(fixture.request(fixture.userOne, key, divergentQuantity));

        assertThat(conflict.error).isInstanceOfSatisfying(TokenMarketException.class,
                exception -> assertThat(exception.reason())
                        .isEqualTo(TokenMarketException.Reason.IDEMPOTENCY_CONFLICT));
        assertThat(longValue("SELECT COUNT(*) FROM market_confirmed_transactions "
                + "WHERE user_id = ?", fixture.userOne)).isLessThanOrEqualTo(1L);
        assertThat(longValue("SELECT COUNT(*) FROM market_movements m "
                + "JOIN market_confirmed_transactions t ON t.id = m.transaction_id "
                + "WHERE t.user_id = ?", fixture.userOne)).isEqualTo(
                longValue("SELECT COUNT(*) FROM market_confirmed_transactions WHERE user_id = ?",
                        fixture.userOne) * 4L);
        assertThat(longValue("SELECT COALESCE(SUM(quantity), 0) FROM market_holdings "
                + "WHERE user_id = ? AND player_id = ?", fixture.userOne, fixture.playerId))
                .isEqualTo(confirmedQuantity);
    }

    @Test
    void compraEInvalidacionConcurrentesSeSerializanPorLaCabecera() throws Exception {
        Fixture fixture = fixture(1, new BigDecimal("100.00"), 100);
        String key = "quote-race";
        List<CallOutcome> outcomes = concurrently(
                () -> purchase(fixture.request(fixture.userOne, key, 1)),
                () -> invalidate(fixture));

        long confirmedTransactions = longValue("SELECT COUNT(*) FROM market_confirmed_transactions "
                + "WHERE user_id = ?", fixture.userOne);
        String headerStatus = stringValue("SELECT validity_status FROM market_quote_headers "
                + "WHERE player_id = ?", fixture.playerId);

        assertThat(confirmedTransactions).isBetween(0L, 1L);
        assertThat(headerStatus).isEqualTo("INVALIDATED");
        assertThat(outcomes.stream().filter(outcome -> outcome.result instanceof QuoteInvalidationResult)
                .count()).isEqualTo(1);
        assertThat(stringValue("SELECT validity_status FROM market_quote_versions "
                + "WHERE quote_id = ? AND version = 1", fixture.quoteId)).isEqualTo("VALID");
        assertThat(outcomes.stream().filter(this::failed).count()).isLessThanOrEqualTo(1);
        assertThat(longValue("SELECT COUNT(*) FROM market_movements m "
                + "JOIN market_confirmed_transactions t ON t.id = m.transaction_id "
                + "WHERE t.user_id = ?", fixture.userOne)).isEqualTo(confirmedTransactions * 4L);
        if (confirmedTransactions == 1) {
            assertThat(stringValue("SELECT quote_id::text FROM market_confirmed_transactions "
                    + "WHERE user_id = ?", fixture.userOne))
                    .isEqualTo(fixture.quoteId.toString());
            assertThat(longValue("SELECT quote_version FROM market_confirmed_transactions "
                    + "WHERE user_id = ?", fixture.userOne)).isEqualTo(1L);
            assertThat(longValue("SELECT COALESCE(SUM(quantity), 0) FROM market_holdings "
                    + "WHERE user_id = ? AND player_id = ?", fixture.userOne, fixture.playerId))
                    .isEqualTo(1L);
        } else {
            assertThat(decimalValue("SELECT balance FROM market_credit_accounts "
                    + "WHERE user_id = ?", fixture.userOne)).isEqualByComparingTo("100.00");
            assertThat(longValue("SELECT inventory_quantity FROM market_token_issuances "
                    + "WHERE player_id = ?", fixture.playerId)).isEqualTo(100L);
            assertThat(longValue("SELECT COALESCE(SUM(quantity), 0) FROM market_holdings "
                    + "WHERE user_id = ? AND player_id = ?", fixture.userOne, fixture.playerId))
                    .isZero();
        }
    }

    private CallOutcome purchase(MarketPurchaseRequest request) {
        try {
            return new CallOutcome(purchases.comprar(request), null);
        } catch (Throwable failure) {
            return new CallOutcome(null, failure);
        }
    }

    private CallOutcome invalidate(Fixture fixture) {
        try {
            return new CallOutcome(quotes.invalidate(new QuoteInvalidationRequest(
                    fixture.playerId, fixture.quoteId, 1L, "concurrent valuation refresh")), null);
        } catch (Throwable failure) {
            return new CallOutcome(null, failure);
        }
    }

    @SafeVarargs
    private final List<CallOutcome> concurrently(Callable<CallOutcome>... calls) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(calls.length);
        executors.add(executor);
        CyclicBarrier barrier = new CyclicBarrier(calls.length + 1);
        List<Future<CallOutcome>> futures = new ArrayList<>();
        for (Callable<CallOutcome> call : calls) {
            futures.add(executor.submit(() -> {
                barrier.await(START_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                return call.call();
            }));
        }
        barrier.await(START_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        List<CallOutcome> outcomes = new ArrayList<>();
        for (Future<CallOutcome> future : futures) {
            outcomes.add(future.get(COMPLETION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
        }
        return outcomes;
    }

    private Fixture fixture(int users, BigDecimal balance, long inventory) {
        UUID playerId = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();
        UUID userOne = createUser("one", balance);
        UUID userTwo = users == 2 ? createUser("two", balance) : userOne;
        CounterpartyFixture counterparty = findOrCreateSuperuser();
        MarketStateSnapshot previousState = marketState();
        UUID teamId = jdbc.queryForObject("SELECT id FROM equipos ORDER BY id LIMIT 1", UUID.class);

        jdbc.update("INSERT INTO market_state (id, status, epoch, created_at, updated_at) "
                + "VALUES (1, 'READY', 2, ?, ?) ON CONFLICT (id) DO UPDATE "
                + "SET status = 'READY', updated_at = GREATEST(market_state.created_at, "
                + "EXCLUDED.updated_at)", NOW, NOW);
        jdbc.update("INSERT INTO jugadores (id, name, position, jersey_number, nationality, "
                + "team_id, is_active, created_at, updated_at) VALUES (?, 'Concurrency Player', "
                + "'Forward', 9, 'Argentina', ?, TRUE, ?, ?)", playerId, teamId, NOW, NOW);
        jdbc.update("INSERT INTO market_credit_accounts (id, user_id, counterparty_id, balance, "
                + "created_at, updated_at) VALUES (?, ?, NULL, ?, ?, ?)",
                UUID.randomUUID(), userOne, balance, NOW, NOW);
        if (users == 2) {
            jdbc.update("INSERT INTO market_credit_accounts (id, user_id, counterparty_id, balance, "
                    + "created_at, updated_at) VALUES (?, ?, NULL, ?, ?, ?)",
                    UUID.randomUUID(), userTwo, balance, NOW, NOW);
        }
        jdbc.update("INSERT INTO market_credit_accounts (id, user_id, counterparty_id, balance, "
                + "created_at, updated_at) SELECT ?, NULL, ?, 0.00, ?, ? "
                + "WHERE NOT EXISTS (SELECT 1 FROM market_credit_accounts WHERE counterparty_id = ?)",
                UUID.randomUUID(), counterparty.id(), NOW, NOW, counterparty.id());
        jdbc.update("INSERT INTO market_token_issuances (player_id, counterparty_id, issued_quantity, "
                + "inventory_quantity, created_at, updated_at) VALUES (?, ?, 100, ?, ?, ?)",
                playerId, counterparty.id(), inventory, NOW, NOW);
        jdbc.update("INSERT INTO market_quote_versions (quote_id, version, player_id, price_per_token, "
                + "published_at, valid_from, validity_status, created_at) VALUES (?, 1, ?, 1.25, ?, ?, "
                + "'VALID', ?)", quoteId, playerId, NOW, NOW, NOW);
        jdbc.update("INSERT INTO market_quote_headers (player_id, quote_id, current_version, updated_at, "
                + "validity_status) VALUES (?, ?, 1, ?, 'VALID')", playerId, quoteId, NOW);
        Fixture fixture = new Fixture(userOne, userTwo, playerId, counterparty.id(), quoteId,
                counterparty.accountId(), counterparty.balanceBefore(), counterparty.accountCreated(),
                counterparty.created(), previousState);
        fixtures.add(fixture);
        return fixture;
    }

    private UUID createUser(String suffix, BigDecimal balance) {
        UUID userId = UUID.randomUUID();
        jdbc.update("INSERT INTO usuarios (id, username, email, password_hash, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)", userId,
                "conc" + suffix + userId.toString().replace("-", "").substring(0, 16),
                userId + "@example.test", PASSWORD_HASH, NOW, NOW);
        return userId;
    }

    private CounterpartyFixture findOrCreateSuperuser() {
        List<UUID> ids = jdbc.query("SELECT id FROM market_counterparties "
                + "WHERE counterparty_type = 'SUPERUSER' LIMIT 1",
                (rs, row) -> rs.getObject(1, UUID.class));
        boolean created = ids.isEmpty();
        UUID id;
        if (created) {
            id = UUID.randomUUID();
            jdbc.update("INSERT INTO market_counterparties (id, counterparty_type, display_name, "
                    + "created_at) VALUES (?, 'SUPERUSER', 'Concurrency Superuser', ?)", id, NOW);
        } else {
            id = ids.get(0);
        }
        List<AccountSnapshot> accounts = jdbc.query("SELECT id, balance FROM market_credit_accounts "
                        + "WHERE counterparty_id = ?", (rs, row) -> new AccountSnapshot(
                                rs.getObject("id", UUID.class), rs.getBigDecimal("balance")), id);
        if (accounts.isEmpty()) {
            UUID accountId = UUID.randomUUID();
            jdbc.update("INSERT INTO market_credit_accounts (id, user_id, counterparty_id, balance, "
                    + "created_at, updated_at) VALUES (?, NULL, ?, 0.00, ?, ?)",
                    accountId, id, NOW, NOW);
            return new CounterpartyFixture(id, accountId, BigDecimal.ZERO, true, created);
        }
        AccountSnapshot account = accounts.get(0);
        return new CounterpartyFixture(id, account.id(), account.balance(), false, created);
    }

    private MarketStateSnapshot marketState() {
        List<MarketStateSnapshot> states = jdbc.query("SELECT status, epoch, "
                        + "initial_reserve_configured, initial_reserve_initialized, failure_reason, "
                        + "created_at, updated_at FROM market_state WHERE id = 1",
                (rs, row) -> new MarketStateSnapshot(rs.getString("status"),
                        rs.getLong("epoch"), rs.getObject("initial_reserve_configured", BigDecimal.class),
                        rs.getObject("initial_reserve_initialized", BigDecimal.class),
                        rs.getString("failure_reason"), rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class)));
        return states.isEmpty() ? null : states.get(0);
    }

    private void cleanupFixture(Fixture fixture) {
        // Las transacciones confirmadas, sus movimientos y las versiones de cotización
        // son historiales inmutables por diseño. No se pueden borrar para limpiar el
        // fixture: se conservan y se eliminan únicamente las filas mutables que no
        // queden referenciadas por ese historial.
        jdbc.update("DELETE FROM market_rejected_attempts WHERE user_id IN (?, ?)",
                fixture.userOne(), fixture.userTwo());
        jdbc.update("DELETE FROM market_idempotency_requests r "
                        + "WHERE r.user_id IN (?, ?) "
                        + "AND NOT EXISTS (SELECT 1 FROM market_confirmed_transactions t "
                        + "WHERE t.idempotency_request_id = r.id)",
                fixture.userOne(), fixture.userTwo());
        jdbc.update("DELETE FROM market_holdings WHERE user_id IN (?, ?) AND player_id = ?",
                fixture.userOne(), fixture.userTwo(), fixture.playerId());
        jdbc.update("DELETE FROM market_realized_pnl WHERE user_id IN (?, ?)",
                fixture.userOne(), fixture.userTwo());
        jdbc.update("DELETE FROM market_credit_accounts a WHERE a.user_id IN (?, ?) "
                        + "AND NOT EXISTS (SELECT 1 FROM market_confirmed_transactions t "
                        + "WHERE t.user_id = a.user_id) "
                        + "AND NOT EXISTS (SELECT 1 FROM market_movements m "
                        + "WHERE m.credit_account_id = a.id OR m.token_user_id = a.user_id) "
                        + "AND NOT EXISTS (SELECT 1 FROM market_initial_credit_grants g "
                        + "WHERE g.credit_account_id = a.id OR g.user_id = a.user_id)",
                fixture.userOne(), fixture.userTwo());
        jdbc.update("DELETE FROM market_quote_headers WHERE player_id = ?", fixture.playerId());
        jdbc.update("DELETE FROM market_token_issuances WHERE player_id = ?", fixture.playerId());

        jdbc.update("DELETE FROM jugadores p WHERE p.id = ? "
                        + "AND NOT EXISTS (SELECT 1 FROM market_quote_versions q "
                        + "WHERE q.player_id = p.id) "
                        + "AND NOT EXISTS (SELECT 1 FROM market_confirmed_transactions t "
                        + "WHERE t.player_id = p.id) "
                        + "AND NOT EXISTS (SELECT 1 FROM market_holdings h "
                        + "WHERE h.player_id = p.id)", fixture.playerId());
        jdbc.update("DELETE FROM usuarios u WHERE u.id IN (?, ?) "
                        + "AND NOT EXISTS (SELECT 1 FROM market_confirmed_transactions t "
                        + "WHERE t.user_id = u.id) "
                        + "AND NOT EXISTS (SELECT 1 FROM market_movements m "
                        + "WHERE m.token_user_id = u.id) "
                        + "AND NOT EXISTS (SELECT 1 FROM market_idempotency_requests r "
                        + "WHERE r.user_id = u.id) "
                        + "AND NOT EXISTS (SELECT 1 FROM market_initial_credit_grants g "
                        + "WHERE g.user_id = u.id)", fixture.userOne(), fixture.userTwo());

        if (fixture.counterpartyAccountCreated()) {
            jdbc.update("DELETE FROM market_credit_accounts a WHERE a.id = ? "
                            + "AND NOT EXISTS (SELECT 1 FROM market_movements m "
                            + "WHERE m.credit_account_id = a.id)",
                    fixture.counterpartyAccountId());
        } else {
            jdbc.update("UPDATE market_credit_accounts SET balance = ? WHERE id = ?",
                    fixture.counterpartyBalanceBefore(), fixture.counterpartyAccountId());
        }
        if (fixture.counterpartyCreated()) {
            jdbc.update("DELETE FROM market_counterparties c WHERE c.id = ? "
                            + "AND NOT EXISTS (SELECT 1 FROM market_credit_accounts a "
                            + "WHERE a.counterparty_id = c.id) "
                            + "AND NOT EXISTS (SELECT 1 FROM market_token_issuances i "
                            + "WHERE i.counterparty_id = c.id) "
                            + "AND NOT EXISTS (SELECT 1 FROM market_confirmed_transactions t "
                            + "WHERE t.counterparty_id = c.id)", fixture.counterpartyId());
        }
        restoreMarketState(fixture.previousState());
    }

    private void restoreMarketState(MarketStateSnapshot state) {
        if (state == null) {
            jdbc.update("DELETE FROM market_state WHERE id = 1");
            return;
        }
        jdbc.update("UPDATE market_state SET status = ?, epoch = ?, initial_reserve_configured = ?, "
                        + "initial_reserve_initialized = ?, failure_reason = ?, created_at = ?, updated_at = ? "
                        + "WHERE id = 1", state.status(), state.epoch(), state.configured(),
                state.initialized(), state.failureReason(), state.createdAt(), state.updatedAt());
    }

    private boolean confirmed(CallOutcome outcome) {
        return outcome.result instanceof MarketPurchaseResult result
                && result.estado() == MarketIdempotencyStatus.CONFIRMED;
    }

    private boolean failed(CallOutcome outcome) {
        return outcome.error != null;
    }

    private boolean isAllowedIdempotentOutcome(CallOutcome outcome) {
        if (outcome.error == null) {
            return outcome.result instanceof MarketPurchaseResult result
                    && (result.estado() == MarketIdempotencyStatus.CONFIRMED
                    || result.estado() == MarketIdempotencyStatus.PROCESSING);
        }
        return outcome.error instanceof TokenMarketException exception
                && exception.reason() == TokenMarketException.Reason.IDEMPOTENCY_CONFLICT;
    }

    private long longValue(String sql, Object... args) {
        Number value = jdbc.queryForObject(sql, Number.class, args);
        return value == null ? 0L : value.longValue();
    }

    private BigDecimal decimalValue(String sql, Object... args) {
        return jdbc.queryForObject(sql, BigDecimal.class, args);
    }

    private String stringValue(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }

    private record CallOutcome(Object result, Throwable error) { }

    private record Fixture(UUID userOne, UUID userTwo, UUID playerId,
            UUID counterpartyId, UUID quoteId, UUID counterpartyAccountId,
            BigDecimal counterpartyBalanceBefore, boolean counterpartyAccountCreated,
            boolean counterpartyCreated, MarketStateSnapshot previousState) {
        private MarketPurchaseRequest request(UUID userId, String key, long quantity) {
            return new MarketPurchaseRequest(userId, key, playerId, quantity, quoteId, 1L);
        }
    }

    private record CounterpartyFixture(UUID id, UUID accountId, BigDecimal balanceBefore,
            boolean accountCreated, boolean created) { }

    private record AccountSnapshot(UUID id, BigDecimal balance) { }

    private record MarketStateSnapshot(String status, long epoch, BigDecimal configured,
            BigDecimal initialized, String failureReason, OffsetDateTime createdAt,
            OffsetDateTime updatedAt) { }
}
