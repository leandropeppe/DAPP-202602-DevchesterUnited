package com.devchesterunited.devchesterunitedv2.integration.persistence;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PCancelCommand;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PCancelResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseCommand;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PQuoteIdentity;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2PCancellationService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2PPurchaseService;
import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.support.fixtures.market.MarketP2PFixtureBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "token-market.initialization.auto-start=false")
class MarketP2PCancelPurchaseConcurrencyIntegrationTest extends PostgresTestSupport {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MarketP2PPurchaseService purchases;

    @Autowired
    private MarketP2PCancellationService cancellations;

    private MarketP2PFixtureBuilder.FixtureUser seller;
    private MarketP2PFixtureBuilder.FixtureUser buyer;
    private MarketP2PFixtureBuilder.FixturePlayer player;
    private MarketP2PFixtureBuilder.FixtureQuote quote;
    private MarketP2PFixtureBuilder.FixtureOffer offer;

    @BeforeEach
    void fixture() {
        MarketP2PFixtureBuilder builder = new MarketP2PFixtureBuilder(jdbc);
        seller = builder.createUser("cancel-purchase-seller", BigDecimal.ZERO.setScale(2));
        buyer = builder.createUser("cancel-purchase-buyer", new BigDecimal("100.00"));
        player = builder.createPlayer("P2P Cancel Purchase Player");
        builder.createHolding(seller, player, 1L, new BigDecimal("10.00000000"));
        builder.createHolding(buyer, player, 0L, BigDecimal.ZERO.setScale(8));
        quote = builder.createQuote(player, new BigDecimal("2.50"), 1L);
        MarketP2PFixtureBuilder.FixtureIdempotency request = builder.createIdempotency(
                seller, "cancel-purchase-offer-" + seller.id(), "P2P_OFFER", "a".repeat(64));
        offer = builder.createOffer(seller, player, request, 1L);
        jdbc.update("UPDATE market_holdings SET reserved_quantity = 1 "
                + "WHERE user_id = ? AND player_id = ?", seller.id(), player.id());
        jdbc.update("INSERT INTO market_state (id, status, epoch, created_at, updated_at) "
                + "VALUES (1, 'READY', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
                + "ON CONFLICT (id) DO UPDATE SET status = 'READY'");
    }

    @Test
    void compraYCancelacionConfirmanComoMaximoUnaOperacion() throws Exception {
        List<Outcome> outcomes = concurrently(
                () -> purchases.comprar(new MarketP2PPurchaseCommand(buyer.id(),
                        "cancel-purchase-buy-key", offer.id(), player.id(), 1L,
                        new MarketP2PQuoteIdentity(quote.quoteId(), quote.version()))),
                () -> cancellations.cancelar(new MarketP2PCancelCommand(seller.id(), offer.id(),
                        "cancel-purchase-cancel-key")));

        assertThat(outcomes.stream().filter(Outcome::successful).count()).isEqualTo(1L);
        assertThat(outcomes.stream().filter(outcome -> !outcome.successful()).count()).isEqualTo(1L);
        assertThat(outcomes.stream().filter(outcome -> !outcome.successful())
                .allMatch(outcome -> outcome.error() instanceof TokenMarketException)).isTrue();
        String status = jdbc.queryForObject("SELECT status FROM market_p2p_offers WHERE id = ?",
                String.class, offer.id());
        assertThat(status).isIn("FILLED", "CANCELLED");
        assertThat(jdbc.queryForObject("SELECT remaining_quantity FROM market_p2p_offers "
                + "WHERE id = ?", Long.class, offer.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM market_holdings "
                + "WHERE user_id = ? AND player_id = ?", Long.class, seller.id(), player.id()))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT quantity FROM market_holdings "
                + "WHERE user_id = ? AND player_id = ?", Long.class, seller.id(), player.id())
                + jdbc.queryForObject("SELECT quantity FROM market_holdings "
                        + "WHERE user_id = ? AND player_id = ?", Long.class, buyer.id(), player.id()))
                .isEqualTo(1L);
        long trades = jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_trades "
                + "WHERE offer_id = ?", Long.class, offer.id());
        long cancellationCount = jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_offer_cancellations "
                + "WHERE offer_id = ?", Long.class, offer.id());
        long movements = jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_movements "
                + "WHERE trade_id IN (SELECT id FROM market_p2p_trades WHERE offer_id = ?)",
                Long.class, offer.id());
        long entries = jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_trade_entries "
                + "WHERE trade_id IN (SELECT id FROM market_p2p_trades WHERE offer_id = ?)",
                Long.class, offer.id());
        if ("FILLED".equals(status)) {
            assertThat(trades).isEqualTo(1L);
            assertThat(cancellationCount).isZero();
            assertThat(movements).isEqualTo(4L);
            assertThat(entries).isEqualTo(2L);
        } else {
            assertThat(trades).isZero();
            assertThat(cancellationCount).isEqualTo(1L);
            assertThat(movements).isZero();
            assertThat(entries).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT balance FROM market_credit_accounts WHERE user_id = ?",
                BigDecimal.class, seller.id()).add(jdbc.queryForObject(
                        "SELECT balance FROM market_credit_accounts WHERE user_id = ?", BigDecimal.class,
                        buyer.id()))).isEqualByComparingTo("100.00");
    }

    private List<Outcome> concurrently(Callable<?> first, Callable<?> second) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(3);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var futures = List.of(executor.submit(() -> run(barrier, first)),
                    executor.submit(() -> run(barrier, second)));
            awaitBarrier(barrier, java.time.Duration.ofSeconds(20));
            return List.of(futures.get(0).get(30, TimeUnit.SECONDS),
                    futures.get(1).get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private Outcome run(CyclicBarrier barrier, Callable<?> operation) {
        try {
            awaitBarrier(barrier, java.time.Duration.ofSeconds(20));
            return new Outcome(operation.call(), null);
        } catch (Exception exception) {
            return new Outcome(null, exception);
        }
    }

    private record Outcome(Object result, Throwable error) {
        private boolean successful() {
            return result instanceof MarketP2PPurchaseResult
                    || result instanceof MarketP2PCancelResult;
        }
    }
}
