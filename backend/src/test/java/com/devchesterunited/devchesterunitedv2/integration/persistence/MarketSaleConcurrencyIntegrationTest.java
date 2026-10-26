package com.devchesterunited.devchesterunitedv2.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyStatus;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketSaleRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketSaleResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteInvalidationRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.QuoteInvalidationResult;
import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketSaleService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.QuotePublicationPort;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * T051: carreras reales de ventas contra una base PostgreSQL exclusiva.
 *
 * <p>Los escenarios se ejecutan deliberadamente en una secuencia acumulativa:
 * las ventas confirmadas conservan sus movimientos y reducen la reserva real
 * del SUPERUSER. No se restauran saldos ni se eliminan historiales; el
 * contenedor exclusivo descarta toda la base al finalizar la clase.</p>
 */
@SpringBootTest(properties = "token-market.initialization.auto-start=false")
@Testcontainers
class MarketSaleConcurrencyIntegrationTest {
    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 10, 15, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final String PASSWORD_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoO5V5f3n0e0D8n8f2h5x0M5K2Qf7tQm2S";
    private static final Duration START_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration COMPLETION_TIMEOUT = Duration.ofSeconds(30);
    private static final BigDecimal INITIAL_RESERVE = new BigDecimal("20.00");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("devchester_sale_concurrency_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MarketSaleService sales;
    @Autowired private QuotePublicationPort quotes;

    @Test
    void ejecutaVentasConcurrentesConEstadoContableAcumulativo() throws Exception {
        initializeMarket();

        Fixture holdingRace = fixture(1L);
        List<CallOutcome> holdingOutcomes = concurrently(
                () -> sell(holdingRace.request(holdingRace.userIds().get(0), "sale-holding-a", 1L)),
                () -> sell(holdingRace.request(holdingRace.userIds().get(0), "sale-holding-b", 1L)));
        assertAtMostOneConfirmed(holdingOutcomes);
        assertScenarioInvariants(holdingRace);

        Fixture idempotent = fixture(1L);
        String sameKey = "sale-idempotent-" + UUID.randomUUID();
        List<CallOutcome> idempotentOutcomes = concurrently(
                () -> sell(idempotent.request(idempotent.userIds().get(0), sameKey, 1L)),
                () -> sell(idempotent.request(idempotent.userIds().get(0), sameKey, 1L)));
        assertThat(idempotentOutcomes)
                .isNotEmpty()
                .allMatch(this::isAllowedIdempotentOutcome);
        assertThat(countIdempotency(idempotent.userIds().get(0), sameKey)).isEqualTo(1L);
        assertThat(countTransactions(idempotent.playerId())).isLessThanOrEqualTo(1L);
        assertScenarioInvariants(idempotent);

        Fixture conflict = fixture(2L);
        String conflictKey = "sale-conflict-" + UUID.randomUUID();
        List<CallOutcome> conflictOutcomes = concurrently(
                () -> sell(conflict.request(conflict.userIds().get(0), conflictKey, 1L)),
                () -> sell(conflict.request(conflict.userIds().get(0), conflictKey, 2L)));
        assertThat(conflictOutcomes).hasSize(2);
        long confirmedQuantity = confirmedQuantity(conflict.playerId());
        assertThat(confirmedQuantity).isIn(1L, 2L);
        long divergentQuantity = confirmedQuantity == 1L ? 2L : 1L;
        CallOutcome conflictResult = sell(
                conflict.request(conflict.userIds().get(0), conflictKey, divergentQuantity));
        if (confirmedQuantity > 0L) {
            assertThat(conflictResult.error).isInstanceOfSatisfying(TokenMarketException.class,
                    exception -> assertThat(exception.reason())
                            .isEqualTo(TokenMarketException.Reason.IDEMPOTENCY_CONFLICT));
        }
        assertThat(countIdempotency(conflict.userIds().get(0), conflictKey)).isEqualTo(1L);
        assertThat(countTransactions(conflict.playerId())).isLessThanOrEqualTo(1L);
        assertScenarioInvariants(conflict);

        Fixture quoteRace = fixture(1L);
        String quoteKey = "sale-quote-race-" + UUID.randomUUID();
        List<CallOutcome> quoteOutcomes = concurrently(
                () -> sell(quoteRace.request(quoteRace.userIds().get(0), quoteKey, 1L)),
                () -> invalidate(quoteRace));
        assertThat(quoteOutcomes.stream()
                .filter(outcome -> outcome.result instanceof QuoteInvalidationResult)
                .count()).isEqualTo(1L);
        assertThat(stringValue("SELECT validity_status FROM market_quote_headers "
                + "WHERE player_id = ?", quoteRace.playerId())).isEqualTo("INVALIDATED");
        assertThat(stringValue("SELECT validity_status FROM market_quote_versions "
                + "WHERE quote_id = ? AND version = 1", quoteRace.quoteId()))
                .isEqualTo("VALID");
        assertScenarioInvariants(quoteRace);

        BigDecimal remainingReserve = decimalValue("SELECT balance FROM market_credit_accounts "
                + "WHERE counterparty_id = ?", superuserId());
        long reserveQuantity = remainingReserve.longValueExact();
        assertThat(remainingReserve).isPositive();
        Fixture reserveRace = fixture(reserveQuantity, reserveQuantity);
        List<CallOutcome> reserveOutcomes = concurrently(
                () -> sell(reserveRace.request(reserveRace.userIds().get(0),
                        "sale-reserve-a", reserveQuantity)),
                () -> sell(reserveRace.request(reserveRace.userIds().get(1),
                        "sale-reserve-b", reserveQuantity)));
        assertThat(reserveOutcomes.stream().filter(this::confirmed).count()).isEqualTo(1L);
        assertThat(decimalValue("SELECT balance FROM market_credit_accounts "
                + "WHERE counterparty_id = ?", superuserId())).isZero();
        assertScenarioInvariants(reserveRace);
    }

    private void initializeMarket() {
        UUID counterparty = UUID.randomUUID();
        jdbc.update("INSERT INTO market_state (id, status, epoch, created_at, updated_at) "
                + "VALUES (1, 'READY', 1, ?, ?)", NOW, NOW);
        jdbc.update("INSERT INTO market_counterparties "
                + "(id, counterparty_type, display_name, created_at) VALUES (?, 'SUPERUSER', "
                + "'Concurrency Superuser', ?)", counterparty, NOW);
        jdbc.update("INSERT INTO market_credit_accounts "
                + "(id, user_id, counterparty_id, balance, created_at, updated_at) "
                + "VALUES (?, NULL, ?, ?, ?, ?)", UUID.randomUUID(), counterparty,
                INITIAL_RESERVE, NOW, NOW);
    }

    private Fixture fixture(long... holdingQuantities) {
        UUID playerId = UUID.randomUUID();
        BigDecimal superuserBalanceBefore = decimalValue("SELECT balance FROM "
                + "market_credit_accounts WHERE counterparty_id = ?", superuserId());
        UUID teamId = jdbc.queryForObject("SELECT id FROM equipos ORDER BY id LIMIT 1", UUID.class);
        jdbc.update("INSERT INTO jugadores (id, name, position, jersey_number, nationality, "
                + "team_id, is_active, created_at, updated_at) VALUES (?, 'Sale Race Player', "
                + "'Forward', 9, 'Argentina', ?, TRUE, ?, ?)", playerId, teamId, NOW, NOW);

        List<UUID> users = new ArrayList<>();
        long totalHolding = 0L;
        for (int index = 0; index < holdingQuantities.length; index++) {
            UUID userId = createUser("sale" + index);
            users.add(userId);
            long quantity = holdingQuantities[index];
            totalHolding += quantity;
            jdbc.update("INSERT INTO market_credit_accounts "
                    + "(id, user_id, counterparty_id, balance, created_at, updated_at) "
                    + "VALUES (?, ?, NULL, 0.00, ?, ?)", UUID.randomUUID(), userId, NOW, NOW);
            jdbc.update("INSERT INTO market_holdings "
                    + "(user_id, player_id, quantity, acquisition_cost, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?)", userId, playerId, quantity,
                    BigDecimal.valueOf(quantity).multiply(new BigDecimal("0.50")), NOW);
        }
        jdbc.update("INSERT INTO market_token_issuances "
                + "(player_id, counterparty_id, issued_quantity, inventory_quantity, "
                + "created_at, updated_at) VALUES (?, ?, 100, ?, ?, ?)", playerId,
                superuserId(), 100L - totalHolding, NOW, NOW);

        var quote = quotes.publishInitialQuote(playerId, new BigDecimal("1.00"), true);
        BigDecimal initialCost = BigDecimal.valueOf(totalHolding).multiply(new BigDecimal("0.50"));
        return new Fixture(playerId, quote.quoteId(), users, holdingQuantities, totalHolding,
                initialCost, superuserBalanceBefore);
    }

    private UUID createUser(String suffix) {
        UUID userId = UUID.randomUUID();
        String username = "sale-" + suffix + "-" + userId.toString().replace("-", "");
        jdbc.update("INSERT INTO usuarios (id, username, email, password_hash, created_at, "
                + "updated_at) VALUES (?, ?, ?, ?, ?, ?)", userId, username,
                userId + "@example.test", PASSWORD_HASH, NOW, NOW);
        return userId;
    }

    private CallOutcome sell(MarketSaleRequest request) {
        try {
            return new CallOutcome(sales.vender(request), null);
        } catch (Throwable failure) {
            return new CallOutcome(null, failure);
        }
    }

    private CallOutcome invalidate(Fixture fixture) {
        try {
            return new CallOutcome(quotes.invalidate(new QuoteInvalidationRequest(
                    fixture.playerId(), fixture.quoteId(), 1L, "concurrent valuation refresh")), null);
        } catch (Throwable failure) {
            return new CallOutcome(null, failure);
        }
    }

    @SafeVarargs
    private final List<CallOutcome> concurrently(Callable<CallOutcome>... calls) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(calls.length);
        CyclicBarrier barrier = new CyclicBarrier(calls.length + 1);
        List<Future<CallOutcome>> futures = new ArrayList<>();
        try {
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
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private void assertAtMostOneConfirmed(List<CallOutcome> outcomes) {
        assertThat(outcomes).hasSize(2);
        assertThat(outcomes.stream().filter(this::confirmed).count()).isLessThanOrEqualTo(1L);
    }

    private boolean confirmed(CallOutcome outcome) {
        return outcome.result instanceof MarketSaleResult result
                && result.estado() == MarketIdempotencyStatus.CONFIRMED;
    }

    private boolean isAllowedIdempotentOutcome(CallOutcome outcome) {
        if (outcome.error == null) {
            return outcome.result instanceof MarketSaleResult result
                    && (result.estado() == MarketIdempotencyStatus.CONFIRMED
                    || result.estado() == MarketIdempotencyStatus.PROCESSING);
        }
        return false;
    }

    private void assertScenarioInvariants(Fixture fixture) {
        long transactions = countTransactions(fixture.playerId());
        long quantitySold = confirmedQuantity(fixture.playerId());
        long holdings = longValue("SELECT COALESCE(SUM(quantity), 0) FROM market_holdings "
                + "WHERE player_id = ?", fixture.playerId());
        long inventory = longValue("SELECT inventory_quantity FROM market_token_issuances "
                + "WHERE player_id = ?", fixture.playerId());
        assertThat(holdings).isGreaterThanOrEqualTo(0L);
        assertThat(inventory).isBetween(0L, 100L);
        assertThat(inventory + holdings).isEqualTo(100L);
        assertThat(quantitySold).isLessThanOrEqualTo(fixture.totalHolding());
        assertThat(longValue("SELECT COUNT(*) FROM market_movements m "
                + "JOIN market_confirmed_transactions t ON t.id = m.transaction_id "
                + "WHERE t.player_id = ?", fixture.playerId())).isEqualTo(transactions * 4L);
        assertThat(longValue("SELECT COUNT(*) FROM market_confirmed_transactions t "
                + "LEFT JOIN market_movements m ON m.transaction_id = t.id "
                + "WHERE t.player_id = ? AND m.id IS NULL", fixture.playerId())).isZero();

        BigDecimal userCredits = decimalValue("SELECT COALESCE(SUM(m.quantity_delta), 0) "
                + "FROM market_movements m JOIN market_confirmed_transactions t "
                + "ON t.id = m.transaction_id JOIN market_credit_accounts a "
                + "ON a.id = m.credit_account_id WHERE t.player_id = ? AND a.user_id IS NOT NULL",
                fixture.playerId());
        BigDecimal superuserDebits = decimalValue("SELECT COALESCE(SUM(m.quantity_delta), 0) "
                + "FROM market_movements m JOIN market_confirmed_transactions t "
                + "ON t.id = m.transaction_id JOIN market_credit_accounts a "
                + "ON a.id = m.credit_account_id WHERE t.player_id = ? AND a.counterparty_id IS NOT NULL",
                fixture.playerId());
        assertThat(userCredits.add(superuserDebits)).isZero();
        BigDecimal userBalances = BigDecimal.ZERO;
        for (UUID userId : fixture.userIds()) {
            userBalances = userBalances.add(decimalValue("SELECT balance FROM "
                    + "market_credit_accounts WHERE user_id = ?", userId));
        }
        assertThat(userBalances).isEqualByComparingTo(userCredits);
        BigDecimal superuserBalance = decimalValue("SELECT balance FROM market_credit_accounts "
                + "WHERE counterparty_id = ?", superuserId());
        assertThat(superuserBalance).isEqualByComparingTo(
                fixture.superuserBalanceBefore().add(superuserDebits));
        BigDecimal realizedPnl = decimalValue("SELECT COALESCE(SUM(realized_pnl), 0) "
                + "FROM market_confirmed_transactions WHERE player_id = ?", fixture.playerId());
        BigDecimal saleAmount = decimalValue("SELECT COALESCE(SUM(total_amount), 0) "
                + "FROM market_confirmed_transactions WHERE player_id = ?", fixture.playerId());
        BigDecimal currentCost = decimalValue("SELECT COALESCE(SUM(acquisition_cost), 0) "
                + "FROM market_holdings WHERE player_id = ?", fixture.playerId());
        assertThat(currentCost.add(saleAmount.subtract(realizedPnl)))
                .isEqualByComparingTo(fixture.initialCost());
        for (UUID userId : fixture.userIds()) {
            BigDecimal userPnl = decimalValue("SELECT COALESCE(SUM(realized_pnl), 0) "
                    + "FROM market_realized_pnl WHERE user_id = ?", userId);
            BigDecimal transactionPnl = decimalValue("SELECT COALESCE(SUM(realized_pnl), 0) "
                    + "FROM market_confirmed_transactions WHERE user_id = ? AND player_id = ?",
                    userId, fixture.playerId());
            assertThat(userPnl).isEqualByComparingTo(transactionPnl);
        }
        assertThat(longValue("SELECT COUNT(*) FROM market_credit_accounts "
                + "WHERE balance < 0")).isZero();
        assertThat(longValue("SELECT COUNT(*) FROM market_holdings "
                + "WHERE quantity < 0")).isZero();
        assertThat(longValue("SELECT COUNT(*) FROM market_quote_versions "
                + "WHERE player_id = ?", fixture.playerId())).isEqualTo(1L);
        assertThat(longValue("SELECT COUNT(*) FROM market_quote_versions q "
                + "JOIN market_confirmed_transactions t ON t.quote_id = q.quote_id "
                + "AND t.quote_version = q.version WHERE q.player_id = ?", fixture.playerId()))
                .isEqualTo(transactions);
    }

    private long countTransactions(UUID playerId) {
        return longValue("SELECT COUNT(*) FROM market_confirmed_transactions "
                + "WHERE player_id = ?", playerId);
    }

    private long confirmedQuantity(UUID playerId) {
        return longValue("SELECT COALESCE(SUM(quantity), 0) "
                + "FROM market_confirmed_transactions WHERE player_id = ?", playerId);
    }

    private long countIdempotency(UUID userId, String key) {
        return longValue("SELECT COUNT(*) FROM market_idempotency_requests "
                + "WHERE user_id = ? AND idempotency_key = ?", userId, key);
    }

    private UUID superuserId() {
        return jdbc.queryForObject("SELECT id FROM market_counterparties "
                + "WHERE counterparty_type = 'SUPERUSER'", UUID.class);
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

    private record Fixture(UUID playerId, UUID quoteId, List<UUID> userIds,
            long[] initialHoldings, long totalHolding, BigDecimal initialCost,
            BigDecimal superuserBalanceBefore) {
        private MarketSaleRequest request(UUID userId, String key, long quantity) {
            return new MarketSaleRequest(userId, key, playerId, quantity, quoteId, 1L);
        }
    }
}
