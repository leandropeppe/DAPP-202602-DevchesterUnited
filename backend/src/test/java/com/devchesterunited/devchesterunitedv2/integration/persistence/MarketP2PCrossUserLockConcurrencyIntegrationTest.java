package com.devchesterunited.devchesterunitedv2.integration.persistence;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseCommand;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PQuoteIdentity;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketPurchaseRequest;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2PPurchaseService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketPurchaseService;
import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.support.fixtures.market.MarketP2PFixtureBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "token-market.initialization.auto-start=false")
class MarketP2PCrossUserLockConcurrencyIntegrationTest extends PostgresTestSupport {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MarketP2PPurchaseService purchases;

    @Autowired
    private MarketPurchaseService primaryPurchases;

    private MarketP2PFixtureBuilder.FixtureUser seller;
    private MarketP2PFixtureBuilder.FixtureUser buyer;
    private MarketP2PFixtureBuilder.FixturePlayer player;
    private MarketP2PFixtureBuilder.FixtureQuote quote;
    private UUID counterpartyId;
    private UUID counterpartyAccountId;
    private boolean counterpartyCreated;
    private boolean counterpartyAccountCreated;

    @BeforeEach
    void fixture() {
        readyMarket();
    }

    @Test
    void comprasCruzadasConservanBalancesTenenciasYAsientos() throws Exception {
        MarketP2PFixtureBuilder builder = new MarketP2PFixtureBuilder(jdbc);
        seller = builder.createUser("cross-lock-seller", new BigDecimal("100.00"));
        buyer = builder.createUser("cross-lock-buyer", new BigDecimal("100.00"));
        player = builder.createPlayer("P2P Cross Lock Player");
        builder.createHolding(seller, player, 2L, new BigDecimal("2.00000000"));
        builder.createHolding(buyer, player, 2L, new BigDecimal("2.00000000"));
        quote = builder.createQuote(player, new BigDecimal("2.50"), 1L);
        MarketP2PFixtureBuilder.FixtureOffer sellerOffer = createOffer(builder, seller, 1L,
                "cross-lock-seller-offer");
        MarketP2PFixtureBuilder.FixtureOffer buyerOffer = createOffer(builder, buyer, 1L,
                "cross-lock-buyer-offer");
        jdbc.update("UPDATE market_holdings SET reserved_quantity = 1 "
                + "WHERE player_id = ? AND user_id IN (?, ?)", player.id(), seller.id(), buyer.id());

        List<Outcome> outcomes = concurrently("P2P buyer-to-seller", "P2P seller-to-buyer",
                () -> purchase(buyer, sellerOffer, "cross-lock-buy-one"),
                () -> purchase(seller, buyerOffer, "cross-lock-buy-two"));

        assertThat(outcomes).allSatisfy(outcome ->
                assertThat(outcome.error())
                        .as("La operacion %s fallo: %s", outcome.operation(), outcome.error())
                        .isNull());
        assertThat(outcomes.stream().filter(Outcome::successful).count()).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT balance FROM market_credit_accounts WHERE user_id = ?",
                BigDecimal.class, seller.id())).isEqualByComparingTo("100.00");
        assertThat(jdbc.queryForObject("SELECT balance FROM market_credit_accounts WHERE user_id = ?",
                BigDecimal.class, buyer.id())).isEqualByComparingTo("100.00");
        assertThat(jdbc.queryForObject("SELECT SUM(quantity) FROM market_holdings "
                + "WHERE user_id IN (?, ?) AND player_id = ?", Long.class,
                seller.id(), buyer.id(), player.id())).isEqualTo(4L);
        assertThat(jdbc.queryForObject("SELECT SUM(reserved_quantity) FROM market_holdings "
                + "WHERE user_id IN (?, ?) AND player_id = ?", Long.class,
                seller.id(), buyer.id(), player.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_trades "
                + "WHERE offer_id IN (?, ?)", Long.class, sellerOffer.id(), buyerOffer.id()))
                .isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_movements "
                + "WHERE trade_id IN (SELECT id FROM market_p2p_trades WHERE offer_id IN (?, ?))",
                Long.class, sellerOffer.id(), buyerOffer.id())).isEqualTo(8L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_trade_entries "
                + "WHERE trade_id IN (SELECT id FROM market_p2p_trades WHERE offer_id IN (?, ?))",
                Long.class, sellerOffer.id(), buyerOffer.id())).isEqualTo(4L);
    }

    @Test
    void compraP2PYCompraPrimariaConcurrenSinDeadlockNiPerdidaContable() throws Exception {
        MarketP2PFixtureBuilder builder = new MarketP2PFixtureBuilder(jdbc);
        seller = builder.createUser("cross-primary-seller", BigDecimal.ZERO.setScale(2));
        buyer = builder.createUser("cross-primary-buyer", new BigDecimal("100.00"));
        player = builder.createPlayer("P2P Primary Compatibility Player");
        builder.createHolding(seller, player, 1L, new BigDecimal("1.00000000"));
        builder.createHolding(buyer, player, 0L, BigDecimal.ZERO.setScale(8));
        quote = builder.createQuote(player, new BigDecimal("2.50"), 1L);
        MarketP2PFixtureBuilder.FixtureOffer offer = createOffer(builder, seller, 1L,
                "cross-primary-offer");
        jdbc.update("UPDATE market_holdings SET reserved_quantity = 1 "
                + "WHERE user_id = ? AND player_id = ?", seller.id(), player.id());
        UUID counterparty = ensureCounterparty();
        jdbc.update("INSERT INTO market_token_issuances "
                + "(player_id, counterparty_id, issued_quantity, inventory_quantity, "
                + "created_at, updated_at) VALUES (?, ?, 100, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                player.id(), counterparty);

        List<Outcome> outcomes = concurrently("P2P", "PRIMARY",
                () -> purchase(buyer, offer, "cross-primary-p2p"),
                () -> primaryPurchases.comprar(new MarketPurchaseRequest(buyer.id(),
                        "cross-primary-primary", player.id(), 1L, quote.quoteId(), quote.version())));

        assertThat(outcomes).allSatisfy(outcome ->
                assertThat(outcome.error())
                        .as("La operacion %s fallo: %s", outcome.operation(), outcome.error())
                        .isNull());
        assertThat(outcomes.stream().filter(Outcome::successful).count()).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT balance FROM market_credit_accounts WHERE user_id = ?",
                BigDecimal.class, buyer.id())).isEqualByComparingTo("95.00");
        assertThat(jdbc.queryForObject("SELECT quantity FROM market_holdings "
                + "WHERE user_id = ? AND player_id = ?", Long.class, buyer.id(), player.id()))
                .isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM market_holdings "
                + "WHERE user_id = ? AND player_id = ?", Long.class, seller.id(), player.id()))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_trades "
                + "WHERE offer_id = ?", Long.class, offer.id())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_movements "
                + "WHERE token_user_id = ? AND player_id = ?", Long.class, buyer.id(), player.id()))
                .isEqualTo(1L);
    }

    private MarketP2PFixtureBuilder.FixtureOffer createOffer(MarketP2PFixtureBuilder builder,
            MarketP2PFixtureBuilder.FixtureUser owner, long quantity, String key) {
        var request = builder.createIdempotency(owner, key, "P2P_OFFER", "a".repeat(64));
        return builder.createOffer(owner, player, request, quantity);
    }

    private Object purchase(MarketP2PFixtureBuilder.FixtureUser user,
            MarketP2PFixtureBuilder.FixtureOffer offer, String key) {
        return purchases.comprar(new MarketP2PPurchaseCommand(user.id(), key, offer.id(),
                player.id(), 1L, new MarketP2PQuoteIdentity(quote.quoteId(), quote.version())));
    }

    private List<Outcome> concurrently(String firstOperation, String secondOperation,
            Callable<?> first, Callable<?> second) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(3);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var futures = List.of(executor.submit(() -> run(firstOperation, barrier, first)),
                    executor.submit(() -> run(secondOperation, barrier, second)));
            awaitBarrier(barrier, java.time.Duration.ofSeconds(20));
            return List.of(futures.get(0).get(30, TimeUnit.SECONDS),
                    futures.get(1).get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private Outcome run(String operationName, CyclicBarrier barrier, Callable<?> operation) {
        try {
            awaitBarrier(barrier, java.time.Duration.ofSeconds(20));
            return new Outcome(operationName, operation.call(), null);
        } catch (Exception exception) {
            return new Outcome(operationName, null, exception);
        }
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
                    + "VALUES (?, 'SUPERUSER', 'Cross Lock Test Superuser', CURRENT_TIMESTAMP)",
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

    private void readyMarket() {
        jdbc.update("INSERT INTO market_state (id, status, epoch, created_at, updated_at) "
                + "VALUES (1, 'READY', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
                + "ON CONFLICT (id) DO UPDATE SET status = 'READY'");
    }

    private void cleanup() {
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

    @AfterEach
    void cleanupAfterTest() {
        cleanup();
    }

    private record Outcome(String operation, Object result, Throwable error) {
        private boolean successful() {
            return result != null && error == null;
        }
    }
}
