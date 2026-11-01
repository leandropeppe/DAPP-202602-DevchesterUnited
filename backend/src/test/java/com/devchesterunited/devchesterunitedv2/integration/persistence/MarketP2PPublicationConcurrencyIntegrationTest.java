package com.devchesterunited.devchesterunitedv2.integration.persistence;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2POfferCommand;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2POfferResult;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2POfferService;
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
class MarketP2PPublicationConcurrencyIntegrationTest extends PostgresTestSupport {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MarketP2POfferService offers;

    private MarketP2PFixtureBuilder.FixtureUser seller;
    private MarketP2PFixtureBuilder.FixturePlayer player;

    @BeforeEach
    void fixture() {
        MarketP2PFixtureBuilder builder = new MarketP2PFixtureBuilder(jdbc);
        seller = builder.createUser("publication-race-seller", new BigDecimal("100.00"));
        player = builder.createPlayer("P2P Publication Race Player");
        builder.createHolding(seller, player, 2L, new BigDecimal("10.00000000"));
        builder.createQuote(player, new BigDecimal("2.50"), 1L);
        jdbc.update("INSERT INTO market_state (id, status, epoch, created_at, updated_at) "
                + "VALUES (1, 'READY', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
                + "ON CONFLICT (id) DO UPDATE SET status = 'READY'");
    }

    @Test
    void dosPublicacionesSobreLaMismaTenenciaNoSobreReservan() throws Exception {
        List<Outcome> outcomes = concurrently(
                () -> publish("publication-race-one"), () -> publish("publication-race-two"));

        assertThat(outcomes.stream().filter(Outcome::successful).count()).isEqualTo(1L);
        assertThat(outcomes.stream().filter(outcome -> !outcome.successful()).count()).isEqualTo(1L);
        assertThat(outcomes.stream().filter(outcome -> !outcome.successful())
                .allMatch(outcome -> outcome.error() instanceof TokenMarketException)).isTrue();
        Long reserved = jdbc.queryForObject("SELECT reserved_quantity FROM market_holdings "
                + "WHERE user_id = ? AND player_id = ?", Long.class, seller.id(), player.id());
        assertThat(reserved).isBetween(0L, 2L)
                .isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_offers "
                + "WHERE seller_user_id = ? AND player_id = ?", Long.class, seller.id(), player.id()))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COALESCE(SUM(remaining_quantity), 0) "
                + "FROM market_p2p_offers WHERE seller_user_id = ? AND player_id = ? "
                + "AND status IN ('OPEN', 'PARTIALLY_FILLED')", Long.class, seller.id(), player.id()))
                .isEqualTo(reserved);
        assertThat(jdbc.queryForObject("SELECT MIN(reserved_quantity) FROM market_holdings "
                + "WHERE user_id = ? AND player_id = ?", Long.class, seller.id(), player.id()))
                .isGreaterThanOrEqualTo(0L);
    }

    private MarketP2POfferResult publish(String key) {
        return offers.publicar(new MarketP2POfferCommand(seller.id(), player.id(), 2L, key));
    }

    private List<Outcome> concurrently(Callable<MarketP2POfferResult> first,
            Callable<MarketP2POfferResult> second) throws Exception {
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

    private Outcome run(CyclicBarrier barrier, Callable<MarketP2POfferResult> operation) {
        try {
            awaitBarrier(barrier, java.time.Duration.ofSeconds(20));
            return new Outcome(operation.call(), null);
        } catch (Exception exception) {
            return new Outcome(null, exception);
        }
    }

    private record Outcome(MarketP2POfferResult result, Throwable error) {
        private boolean successful() {
            return result != null && result.offerId() != null;
        }
    }
}
