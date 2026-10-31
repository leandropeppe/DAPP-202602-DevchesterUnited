package com.devchesterunited.devchesterunitedv2.integration.persistence;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseCommand;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PQuoteIdentity;
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
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "token-market.initialization.auto-start=false")
class MarketP2PPurchaseConcurrencyIntegrationTest extends PostgresTestSupport {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MarketP2PPurchaseService purchases;

    private MarketP2PFixtureBuilder.FixtureUser seller;
    private MarketP2PFixtureBuilder.FixtureUser buyerOne;
    private MarketP2PFixtureBuilder.FixtureUser buyerTwo;
    private MarketP2PFixtureBuilder.FixturePlayer player;
    private MarketP2PFixtureBuilder.FixtureQuote quote;
    private MarketP2PFixtureBuilder.FixtureOffer offer;

    @BeforeEach
    void fixture() {
        MarketP2PFixtureBuilder builder = new MarketP2PFixtureBuilder(jdbc);
        seller = builder.createUser("purchase-race-seller", BigDecimal.ZERO.setScale(2));
        buyerOne = builder.createUser("purchase-race-one", new BigDecimal("100.00"));
        buyerTwo = builder.createUser("purchase-race-two", new BigDecimal("100.00"));
        player = builder.createPlayer("P2P Purchase Race Player");
        builder.createHolding(seller, player, 1L, new BigDecimal("10.00000000"));
        builder.createHolding(buyerOne, player, 0L, BigDecimal.ZERO.setScale(8));
        builder.createHolding(buyerTwo, player, 0L, BigDecimal.ZERO.setScale(8));
        quote = builder.createQuote(player, new BigDecimal("2.50"), 1L);
        MarketP2PFixtureBuilder.FixtureIdempotency request = builder.createIdempotency(
                seller, "purchase-race-offer-" + seller.id(), "P2P_OFFER", "a".repeat(64));
        offer = builder.createOffer(seller, player, request, 1L);
        jdbc.update("UPDATE market_holdings SET reserved_quantity = 1 "
                + "WHERE user_id = ? AND player_id = ?", seller.id(), player.id());
        jdbc.update("INSERT INTO market_state (id, status, epoch, created_at, updated_at) "
                + "VALUES (1, 'READY', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
                + "ON CONFLICT (id) DO UPDATE SET status = 'READY'");
    }

    @Test
    void dosCompradoresSobreElMismoRemanenteNoGeneranSobreventaNiDuplicados() throws Exception {
        List<Outcome> outcomes = concurrently(
                () -> buy(buyerOne, "purchase-race-one-key"),
                () -> buy(buyerTwo, "purchase-race-two-key"));

        assertThat(outcomes.stream().filter(Outcome::successful).count()).isEqualTo(1L);
        assertThat(outcomes.stream().filter(outcome -> !outcome.successful()).count()).isEqualTo(1L);
        assertThat(outcomes.stream().filter(outcome -> !outcome.successful())
                .allMatch(outcome -> outcome.error() instanceof TokenMarketException)).isTrue();
        assertThat(outcomes.stream().filter(Outcome::successful)
                .allMatch(outcome -> outcome.result().estado().name().equals("CONFIRMED"))).isTrue();
        assertThat(jdbc.queryForObject("SELECT remaining_quantity FROM market_p2p_offers "
                + "WHERE id = ?", Long.class, offer.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT sold_quantity FROM market_p2p_offers "
                + "WHERE id = ?", Long.class, offer.id())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM market_holdings "
                + "WHERE user_id = ? AND player_id = ?", Long.class, seller.id(), player.id()))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT quantity FROM market_holdings "
                + "WHERE user_id = ? AND player_id = ?", Long.class, buyerOne.id(), player.id())
                + jdbc.queryForObject("SELECT quantity FROM market_holdings "
                        + "WHERE user_id = ? AND player_id = ?", Long.class, buyerTwo.id(), player.id()))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_trades "
                + "WHERE offer_id = ?", Long.class, offer.id())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_movements "
                + "WHERE trade_id IN (SELECT id FROM market_p2p_trades WHERE offer_id = ?)",
                Long.class, offer.id())).isEqualTo(4L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_trade_entries "
                + "WHERE trade_id IN (SELECT id FROM market_p2p_trades WHERE offer_id = ?)",
                Long.class, offer.id())).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT balance FROM market_credit_accounts WHERE user_id = ?",
                BigDecimal.class, seller.id())).isEqualByComparingTo("2.50");
        assertThat(jdbc.queryForObject("SELECT balance FROM market_credit_accounts WHERE user_id = ?",
                BigDecimal.class, buyerOne.id()).add(jdbc.queryForObject(
                        "SELECT balance FROM market_credit_accounts WHERE user_id = ?", BigDecimal.class,
                        buyerTwo.id()))).isEqualByComparingTo("197.50");
        assertThat(jdbc.queryForObject("SELECT MIN(remaining_quantity) FROM market_p2p_offers "
                + "WHERE id = ?", Long.class, offer.id())).isGreaterThanOrEqualTo(0L);
    }

    private MarketP2PPurchaseResult buy(MarketP2PFixtureBuilder.FixtureUser buyer, String key) {
        return purchases.comprar(new MarketP2PPurchaseCommand(buyer.id(), key, offer.id(),
                player.id(), 1L, new MarketP2PQuoteIdentity(quote.quoteId(), quote.version())));
    }

    private List<Outcome> concurrently(Callable<MarketP2PPurchaseResult> first,
            Callable<MarketP2PPurchaseResult> second) throws Exception {
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

    private Outcome run(CyclicBarrier barrier, Callable<MarketP2PPurchaseResult> operation) {
        try {
            awaitBarrier(barrier, java.time.Duration.ofSeconds(20));
            return new Outcome(operation.call(), null);
        } catch (Exception exception) {
            return new Outcome(null, exception);
        }
    }

    private record Outcome(MarketP2PPurchaseResult result, Throwable error) {
        private boolean successful() {
            return result != null && result.tradeId() != null;
        }
    }
}
