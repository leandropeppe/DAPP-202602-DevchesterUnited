package com.devchesterunited.devchesterunitedv2.integration.persistence;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PCancelCommand;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PQuoteIdentity;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseCommand;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2POfferCommand;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPurchaseRequest;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2PCancellationService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2POfferService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2PPurchaseService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketPurchaseService;
import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.support.fixtures.market.MarketP2PFixtureBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T061: observa locks reales mientras una operación espera una tenencia. */
@SpringBootTest(properties = "token-market.initialization.auto-start=false")
class MarketP2PLockOrderIntegrationTest extends PostgresTestSupport {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MarketP2PPurchaseService purchases;

    @Autowired
    private MarketPurchaseService primaryPurchases;

    @Autowired
    private MarketP2POfferService offers;

    @Autowired
    private MarketP2PCancellationService cancellations;

    private MarketP2PFixtureBuilder.FixtureUser seller;
    private MarketP2PFixtureBuilder.FixtureUser buyer;
    private MarketP2PFixtureBuilder.FixturePlayer player;
    private UUID counterpartyId;
    private UUID counterpartyAccountId;
    private boolean counterpartyCreated;
    private boolean counterpartyAccountCreated;

    @Test
    void compraAdquiereCuentasAntesDeEsperarTenencias() throws Exception {
        MarketP2PFixtureBuilder.FixtureOffer offer = preparePurchaseFixture();
        try (Connection blocker = openConnection()) {
            beginHolding(blocker, buyer.id(), player.id());
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<MarketP2PPurchaseResult> purchase = executor.submit(() ->
                        purchases.comprar(new MarketP2PPurchaseCommand(
                                buyer.id(), "lock-purchase", offer.id(), player.id(), 1L,
                                new MarketP2PQuoteIdentity(
                                        jdbc.queryForObject("SELECT quote_id FROM market_quote_headers "
                                                + "WHERE player_id = ?", UUID.class, player.id()), 1L))));
                long pid = awaitWaitingPid("market_holdings");
                assertThat(heldRelations(pid)).contains("market_state", "market_p2p_offers",
                        "jugadores", "market_quote_headers", "market_credit_accounts");
                commit(blocker);
                assertThat(purchase.get(10, TimeUnit.SECONDS).tradeId()).isNotNull();
            } finally {
                executor.shutdownNow();
            }
        } finally {
            cleanup();
        }
    }

    @Test
    void publicacionYCancelacionNoAdquierenCuentas() throws Exception {
        MarketP2PFixtureBuilder builder = new MarketP2PFixtureBuilder(jdbc);
        seller = builder.createUser("lock-seller", new BigDecimal("100.00"));
        player = builder.createPlayer("Lock Player");
        builder.createHolding(seller, player, 4L, new BigDecimal("4.00000000"));
        builder.createQuote(player, new BigDecimal("2.50"), 1L);
        readyMarket();
        installPrimaryFailure();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection blocker = openConnection()) {
            beginHolding(blocker, seller.id(), player.id());
            Future<?> publication = executor.submit(() -> offers.publicar(
                    new MarketP2POfferCommand(seller.id(), player.id(), 2L, "lock-offer")));
            long publicationPid = awaitWaitingPid("market_holdings");
            assertThat(heldRelations(publicationPid)).contains("market_state", "jugadores",
                    "market_quote_headers").doesNotContain("market_credit_accounts");
            commit(blocker);
            publication.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            removePrimaryFailure();
        }

        UUID offerId = jdbc.queryForObject("SELECT id FROM market_p2p_offers WHERE seller_user_id = ?",
                UUID.class, seller.id());
        jdbc.update("UPDATE market_holdings SET reserved_quantity = 2 WHERE user_id = ? AND player_id = ?",
                seller.id(), player.id());
        try (Connection blocker = openConnection()) {
            beginHolding(blocker, seller.id(), player.id());
            executor = Executors.newSingleThreadExecutor();
            try {
                Future<?> cancellation = executor.submit(() -> cancellations.cancelar(
                        new MarketP2PCancelCommand(seller.id(), offerId, "lock-cancel")));
                long cancellationPid = awaitWaitingPid("market_holdings");
                assertThat(heldRelations(cancellationPid)).contains("market_state", "market_p2p_offers")
                        .doesNotContain("market_credit_accounts");
                commit(blocker);
                cancellation.get(10, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }
        } finally {
            cleanup();
        }
    }

    @Test
    void compraPrimariaAdquiereCuentaAntesDeEsperarTenencia() throws Exception {
        MarketP2PFixtureBuilder builder = new MarketP2PFixtureBuilder(jdbc);
        buyer = builder.createUser("lock-primary-buyer", new BigDecimal("100.00"));
        player = builder.createPlayer("Lock Primary Player");
        builder.createHolding(buyer, player, 0L, BigDecimal.ZERO.setScale(8));
        MarketP2PFixtureBuilder.FixtureQuote quote = builder.createQuote(
                player, new BigDecimal("2.50"), 1L);
        UUID counterparty = ensureCounterparty();
        jdbc.update("INSERT INTO market_token_issuances "
                        + "(player_id, counterparty_id, issued_quantity, inventory_quantity, "
                        + "created_at, updated_at) VALUES (?, ?, 100, 4, CURRENT_TIMESTAMP, "
                + "CURRENT_TIMESTAMP)", player.id(), counterparty);
        readyMarket();

        installPrimaryFailure();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection blocker = openConnection()) {
            beginHolding(blocker, buyer.id(), player.id());
            Future<?> purchase = executor.submit(() -> primaryPurchases.comprar(
                    new MarketPurchaseRequest(buyer.id(), "lock-primary", player.id(),
                            1L, quote.quoteId(), quote.version())));
            long pid = awaitWaitingPid("market_holdings");
            assertThat(heldRelations(pid)).contains("market_state", "jugadores",
                    "market_quote_headers", "market_credit_accounts",
                    "market_token_issuances");
            commit(blocker);
            assertThatThrownBy(() -> purchase.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(RuntimeException.class);
        } finally {
            executor.shutdownNow();
            removePrimaryFailure();
            cleanup();
        }
    }

    @Test
    void comprasCruzadasUsanDosConexionesYConservanSaldos() throws Exception {
        MarketP2PFixtureBuilder builder = new MarketP2PFixtureBuilder(jdbc);
        seller = builder.createUser("lock-cross-seller", new BigDecimal("100.00"));
        buyer = builder.createUser("lock-cross-buyer", new BigDecimal("100.00"));
        player = builder.createPlayer("Lock Cross Player");
        builder.createHolding(seller, player, 2L, new BigDecimal("2.00000000"));
        builder.createHolding(buyer, player, 2L, new BigDecimal("2.00000000"));
        MarketP2PFixtureBuilder.FixtureQuote quote = builder.createQuote(
                player, new BigDecimal("2.50"), 1L);
        MarketP2PFixtureBuilder.FixtureIdempotency sellerRequest = builder.createIdempotency(
                seller, "cross-offer-seller", "P2P_OFFER", "b".repeat(64));
        MarketP2PFixtureBuilder.FixtureIdempotency buyerRequest = builder.createIdempotency(
                buyer, "cross-offer-buyer", "P2P_OFFER", "c".repeat(64));
        MarketP2PFixtureBuilder.FixtureOffer sellerOffer = builder.createOffer(
                seller, player, sellerRequest, 1L);
        MarketP2PFixtureBuilder.FixtureOffer buyerOffer = builder.createOffer(
                buyer, player, buyerRequest, 1L);
        jdbc.update("UPDATE market_holdings SET reserved_quantity = 1 "
                        + "WHERE player_id = ? AND user_id IN (?, ?)", player.id(), seller.id(), buyer.id());
        readyMarket();

        CyclicBarrier start = new CyclicBarrier(3);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> runAfterBarrier(start, () -> purchases.comprar(
                    purchaseCommand(buyer, sellerOffer, quote, "cross-buy-one"))));
            Future<?> second = executor.submit(() -> runAfterBarrier(start, () -> purchases.comprar(
                    purchaseCommand(seller, buyerOffer, quote, "cross-buy-two"))));
            start.await(10, TimeUnit.SECONDS);
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT balance FROM market_credit_accounts "
                    + "WHERE user_id = ?", BigDecimal.class, seller.id()))
                    .isEqualByComparingTo("100.00");
            assertThat(jdbc.queryForObject("SELECT balance FROM market_credit_accounts "
                    + "WHERE user_id = ?", BigDecimal.class, buyer.id()))
                    .isEqualByComparingTo("100.00");
            assertThat(jdbc.queryForObject("SELECT quantity FROM market_holdings "
                    + "WHERE user_id = ? AND player_id = ?", Long.class, seller.id(), player.id()))
                    .isEqualTo(2L);
            assertThat(jdbc.queryForObject("SELECT quantity FROM market_holdings "
                    + "WHERE user_id = ? AND player_id = ?", Long.class, buyer.id(), player.id()))
                    .isEqualTo(2L);
        } finally {
            executor.shutdownNow();
            cleanup();
        }
    }

    private MarketP2PFixtureBuilder.FixtureOffer preparePurchaseFixture() {
        MarketP2PFixtureBuilder builder = new MarketP2PFixtureBuilder(jdbc);
        seller = builder.createUser("lock-purchase-seller", BigDecimal.ZERO.setScale(2));
        buyer = builder.createUser("lock-purchase-buyer", new BigDecimal("100.00"));
        player = builder.createPlayer("Lock Purchase Player");
        builder.createHolding(seller, player, 4L, new BigDecimal("4.00000000"));
        builder.createHolding(buyer, player, 0L, BigDecimal.ZERO.setScale(8));
        var quote = builder.createQuote(player, new BigDecimal("2.50"), 1L);
        var request = builder.createIdempotency(seller, "lock-offer", "P2P_OFFER", "a".repeat(64));
        var offer = builder.createOffer(seller, player, request, 4L);
        jdbc.update("UPDATE market_holdings SET reserved_quantity = 4 WHERE user_id = ? AND player_id = ?",
                seller.id(), player.id());
        readyMarket();
        assertThat(quote.version()).isEqualTo(1L);
        return offer;
    }

    private void readyMarket() {
        jdbc.update("INSERT INTO market_state (id, status, epoch, created_at, updated_at) "
                + "VALUES (1, 'READY', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
                + "ON CONFLICT (id) DO UPDATE SET status = 'READY'");
    }

    private UUID ensureCounterparty() {
        List<UUID> ids = jdbc.query("SELECT id FROM market_counterparties "
                        + "WHERE counterparty_type = 'SUPERUSER' LIMIT 1",
                (rs, row) -> rs.getObject(1, UUID.class));
        counterpartyCreated = ids.isEmpty();
        counterpartyId = counterpartyCreated ? UUID.randomUUID() : ids.get(0);
        if (counterpartyCreated) {
            jdbc.update("INSERT INTO market_counterparties "
                            + "(id, counterparty_type, display_name, created_at) "
                            + "VALUES (?, 'SUPERUSER', 'Lock Test Superuser', CURRENT_TIMESTAMP)",
                    counterpartyId);
        }
        List<UUID> accountIds = jdbc.query("SELECT id FROM market_credit_accounts "
                        + "WHERE counterparty_id = ? LIMIT 1",
                (rs, row) -> rs.getObject(1, UUID.class), counterpartyId);
        counterpartyAccountCreated = accountIds.isEmpty();
        counterpartyAccountId = counterpartyAccountCreated
                ? UUID.randomUUID() : accountIds.get(0);
        if (counterpartyAccountCreated) {
            jdbc.update("INSERT INTO market_credit_accounts "
                            + "(id, user_id, counterparty_id, balance, created_at, updated_at) "
                            + "VALUES (?, NULL, ?, 0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    counterpartyAccountId, counterpartyId);
        }
        return counterpartyId;
    }

    private MarketP2PPurchaseCommand purchaseCommand(
            MarketP2PFixtureBuilder.FixtureUser user,
            MarketP2PFixtureBuilder.FixtureOffer offer,
            MarketP2PFixtureBuilder.FixtureQuote quote, String key) {
        return new MarketP2PPurchaseCommand(user.id(), key, offer.id(), player.id(),
                1L, new MarketP2PQuoteIdentity(quote.quoteId(), quote.version()));
    }

    private Object runAfterBarrier(CyclicBarrier barrier, java.util.concurrent.Callable<?> work) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
            return work.call();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private void beginHolding(Connection connection, UUID userId, UUID playerId) throws Exception {
        connection.setAutoCommit(false);
        try (var statement = connection.prepareStatement(
                "SELECT user_id FROM market_holdings WHERE user_id = ? AND player_id = ? FOR UPDATE")) {
            statement.setObject(1, userId);
            statement.setObject(2, playerId);
            statement.executeQuery();
        }
    }

    private long awaitWaitingPid(String relation) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            List<Long> pids = jdbc.query("SELECT pid FROM pg_stat_activity "
                    + "WHERE wait_event_type = 'Lock' AND datname = current_database() "
                    + "AND query ILIKE ?",
                    (rs, row) -> rs.getLong(1), "%" + relation + "%");
            if (!pids.isEmpty()) {
                return pids.get(0);
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));
        }
        throw new AssertionError("No se observó una espera de lock en " + relation);
    }

    private Set<String> heldRelations(long pid) {
        return new HashSet<>(jdbc.query("SELECT c.relname FROM pg_locks l "
                + "JOIN pg_class c ON c.oid = l.relation WHERE l.pid = ? AND l.granted "
                + "AND c.relname IN ('market_state', 'market_p2p_offers', 'jugadores', "
                + "'market_quote_headers', 'market_credit_accounts', 'market_holdings', "
                + "'market_token_issuances')",
                (rs, row) -> rs.getString(1), pid));
    }

    private void commit(Connection connection) throws Exception {
        connection.commit();
    }

    private void cleanup() {
        jdbc.execute("ALTER TABLE market_movements "
                + "DISABLE TRIGGER ALL");
        try {
            if (player != null) {
                jdbc.update("DELETE FROM market_movements m USING "
                        + "market_confirmed_transactions t WHERE m.transaction_id = t.id "
                        + "AND t.player_id = ?", player.id());
            }
            if (player != null) {
                limpiarJugadoresYDependencias(jdbc, player.id());
            }
            if (seller != null) {
                limpiarUsuarioYDependencias(jdbc, seller.id());
            }
            if (buyer != null) {
                limpiarUsuarioYDependencias(jdbc, buyer.id());
            }
            cleanupCounterpartyFixture();
        } finally {
            jdbc.execute("ALTER TABLE market_movements "
                    + "ENABLE TRIGGER ALL");
        }
        player = null;
        seller = null;
        buyer = null;
        counterpartyId = null;
        counterpartyAccountId = null;
        counterpartyCreated = false;
        counterpartyAccountCreated = false;
    }

    private void cleanupCounterpartyFixture() {
        if (counterpartyAccountCreated && counterpartyAccountId != null) {
            jdbc.update("DELETE FROM market_credit_accounts a WHERE a.id = ? "
                            + "AND NOT EXISTS (SELECT 1 FROM market_movements m "
                            + "WHERE m.credit_account_id = a.id) "
                            + "AND NOT EXISTS (SELECT 1 FROM market_initial_credit_grants g "
                            + "WHERE g.credit_account_id = a.id)", counterpartyAccountId);
        }
        if (counterpartyCreated && counterpartyId != null) {
            jdbc.update("DELETE FROM market_counterparties c WHERE c.id = ? "
                            + "AND NOT EXISTS (SELECT 1 FROM market_credit_accounts a "
                            + "WHERE a.counterparty_id = c.id) "
                            + "AND NOT EXISTS (SELECT 1 FROM market_token_issuances i "
                            + "WHERE i.counterparty_id = c.id) "
                            + "AND NOT EXISTS (SELECT 1 FROM market_confirmed_transactions t "
                            + "WHERE t.counterparty_id = c.id)", counterpartyId);
        }
    }

    private void installPrimaryFailure() {
        jdbc.execute("CREATE OR REPLACE FUNCTION test_fail_primary_lock() "
                + "RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                + "RAISE EXCEPTION 'forced primary lock observation rollback'; END; $$");
        jdbc.execute("CREATE TRIGGER test_fail_primary_lock_trigger "
                + "BEFORE INSERT ON market_movements FOR EACH ROW "
                + "EXECUTE FUNCTION test_fail_primary_lock()");
    }

    private void removePrimaryFailure() {
        jdbc.execute("DROP TRIGGER IF EXISTS test_fail_primary_lock_trigger "
                + "ON market_movements");
        jdbc.execute("DROP FUNCTION IF EXISTS test_fail_primary_lock()");
    }

    @AfterEach
    void cleanupAfterTest() {
        cleanup();
    }
}
