package com.devchesterunited.devchesterunitedv2.integration.persistence;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseCommand;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PQuoteIdentity;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2PPurchaseService;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "token-market.initialization.auto-start=false")
class MarketP2PIdempotencyConcurrencyIntegrationTest extends PostgresTestSupport {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MarketP2PPurchaseService purchases;

    private MarketP2PFixtureBuilder.FixtureUser seller;
    private MarketP2PFixtureBuilder.FixtureUser buyer;
    private MarketP2PFixtureBuilder.FixturePlayer player;
    private MarketP2PFixtureBuilder.FixtureQuote quote;
    private MarketP2PFixtureBuilder.FixtureOffer offer;

    @BeforeEach
    void fixture() {
        MarketP2PFixtureBuilder builder = new MarketP2PFixtureBuilder(jdbc);
        seller = builder.createUser("idempotency-race-seller", BigDecimal.ZERO.setScale(2));
        buyer = builder.createUser("idempotency-race-buyer", new BigDecimal("100.00"));
        player = builder.createPlayer("P2P Idempotency Race Player");
        builder.createHolding(seller, player, 2L, new BigDecimal("2.00000000"));
        builder.createHolding(buyer, player, 0L, BigDecimal.ZERO.setScale(8));
        quote = builder.createQuote(player, new BigDecimal("2.50"), 1L);
        var request = builder.createIdempotency(seller, "idempotency-offer-" + seller.id(),
                "P2P_OFFER", "a".repeat(64));
        offer = builder.createOffer(seller, player, request, 2L);
        jdbc.update("UPDATE market_holdings SET reserved_quantity = 2 "
                + "WHERE user_id = ? AND player_id = ?", seller.id(), player.id());
        jdbc.update("INSERT INTO market_state (id, status, epoch, created_at, updated_at) "
                + "VALUES (1, 'READY', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
                + "ON CONFLICT (id) DO UPDATE SET status = 'READY'");
    }

    @Test
    void mismaClaveYParametrosIdenticosLiquidaUnaSolaVezYDevuelveElReplay() throws Exception {
        List<Outcome> outcomes = concurrently(
                () -> purchase("same-key", 1L), () -> purchase("same-key", 1L));

        assertThat(outcomes.stream().allMatch(Outcome::successful)).isTrue();
        assertThat(outcomes.stream().filter(Outcome::confirmed).count()).isBetween(1L, 2L);
        MarketP2PPurchaseResult confirmed = outcomes.stream()
                .map(Outcome::result)
                .filter(result -> result.tradeId() != null)
                .findFirst()
                .orElseThrow();
        MarketP2PPurchaseResult replay = purchases.comprar(command("same-key", 1L));
        assertThat(replay.tradeId()).isEqualTo(confirmed.tradeId());
        assertThat(replay.estado().name()).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_idempotency_requests "
                + "WHERE user_id = ? AND idempotency_key = ?", Long.class,
                buyer.id(), "same-key")).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT status FROM market_idempotency_requests "
                + "WHERE user_id = ? AND idempotency_key = ?", String.class,
                buyer.id(), "same-key")).isEqualTo("CONFIRMED");
        assertSettlementCounts(1L, 4L, 2L);
    }

    @Test
    void mismaClaveConParametrosDistintosConflictaSinSegundoEfecto() throws Exception {
        List<Outcome> outcomes = concurrently(
                () -> purchase("conflict-key", 1L), () -> purchase("conflict-key", 2L));

        assertThat(outcomes.stream().filter(Outcome::confirmed).count()).isEqualTo(1L);
        assertThat(outcomes.stream().filter(Outcome::processing).count()
                + outcomes.stream().filter(Outcome::conflict).count()).isEqualTo(1L);
        assertThat(outcomes.stream().filter(outcome -> !outcome.successful())
                .allMatch(Outcome::conflict)).isTrue();
        assertSettlementCounts(1L, 4L, 2L);
        long sold = jdbc.queryForObject("SELECT sold_quantity FROM market_p2p_offers "
                + "WHERE id = ?", Long.class, offer.id());
        assertThat(sold).isIn(1L, 2L);
        assertThat(jdbc.queryForObject("SELECT remaining_quantity FROM market_p2p_offers "
                + "WHERE id = ?", Long.class, offer.id())).isEqualTo(2L - sold);
        MarketP2PPurchaseResult confirmed = outcomes.stream()
                .map(Outcome::result)
                .filter(result -> result != null && result.tradeId() != null)
                .findFirst()
                .orElseThrow();
        long differentQuantity = confirmed.settlement().quantity() == 1L ? 2L : 1L;
        assertThatThrownBy(() -> purchases.comprar(command("conflict-key", differentQuantity)))
                .isInstanceOfSatisfying(TokenMarketException.class, exception ->
                        assertThat(exception.reason())
                                .isEqualTo(TokenMarketException.Reason.IDEMPOTENCY_CONFLICT));
    }

    @Test
    void replayDespuesDePerderLaRespuestaRecuperaElResultadoConfirmado() {
        MarketP2PPurchaseCommand command = command("lost-response-key", 1L);
        MarketP2PPurchaseResult committed = purchases.comprar(command);
        MarketP2PPurchaseResult replay = purchases.comprar(command);

        assertThat(replay).isEqualTo(committed);
        assertSettlementCounts(1L, 4L, 2L);
        assertThat(jdbc.queryForObject("SELECT balance FROM market_credit_accounts WHERE user_id = ?",
                BigDecimal.class, buyer.id())).isEqualByComparingTo("97.50");
    }

    private MarketP2PPurchaseResult purchase(String key, long quantity) {
        return purchases.comprar(command(key, quantity));
    }

    private MarketP2PPurchaseCommand command(String key, long quantity) {
        return new MarketP2PPurchaseCommand(buyer.id(), key, offer.id(), player.id(), quantity,
                new MarketP2PQuoteIdentity(quote.quoteId(), quote.version()));
    }

    private void assertSettlementCounts(long trades, long movements, long entries) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_trades "
                + "WHERE offer_id = ?", Long.class, offer.id())).isEqualTo(trades);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_movements "
                + "WHERE trade_id IN (SELECT id FROM market_p2p_trades WHERE offer_id = ?)",
                Long.class, offer.id())).isEqualTo(movements);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_trade_entries "
                + "WHERE trade_id IN (SELECT id FROM market_p2p_trades WHERE offer_id = ?)",
                Long.class, offer.id())).isEqualTo(entries);
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
        player = null;
        seller = null;
        buyer = null;
    }

    @AfterEach
    void cleanupAfterTest() {
        cleanup();
    }

    private record Outcome(MarketP2PPurchaseResult result, Throwable error) {
        private boolean successful() {
            return result != null && error == null;
        }

        private boolean confirmed() {
            return successful() && result.tradeId() != null;
        }

        private boolean processing() {
            return successful() && result.tradeId() == null
                    && result.estado().name().equals("PROCESSING");
        }

        private boolean conflict() {
            return error instanceof TokenMarketException exception
                    && exception.reason() == TokenMarketException.Reason.IDEMPOTENCY_CONFLICT;
        }
    }
}
