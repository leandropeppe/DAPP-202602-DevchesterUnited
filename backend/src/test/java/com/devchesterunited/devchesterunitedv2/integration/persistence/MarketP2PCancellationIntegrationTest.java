package com.devchesterunited.devchesterunitedv2.integration.persistence;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PCancelCommand;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PCancelResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2POfferQuery;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PQuoteIdentity;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseCommand;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseResult;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2PCancellationService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2POfferQueryService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2PPurchaseService;
import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.support.fixtures.market.MarketP2PFixtureBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
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
class MarketP2PCancellationIntegrationTest extends PostgresTestSupport {
    private static final long OFFER_QUANTITY = 4L;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MarketP2PCancellationService cancellations;

    @Autowired
    private MarketP2PPurchaseService purchases;

    @Autowired
    private MarketP2POfferQueryService offerQueries;

    private MarketP2PFixtureBuilder.FixtureUser seller;
    private MarketP2PFixtureBuilder.FixtureUser buyer;
    private MarketP2PFixtureBuilder.FixturePlayer player;
    private MarketP2PFixtureBuilder.FixtureQuote quote;
    private MarketP2PFixtureBuilder.FixtureOffer offer;

    @BeforeEach
    void fixture() {
        MarketP2PFixtureBuilder builder = new MarketP2PFixtureBuilder(jdbc);
        seller = builder.createUser("cancel-seller", new BigDecimal("0.00"));
        buyer = builder.createUser("cancel-buyer", new BigDecimal("100.00"));
        player = builder.createPlayer("P2P Cancellation Player");
        builder.createHolding(seller, player, 10L, new BigDecimal("10.00000000"));
        builder.createHolding(buyer, player, 0L, BigDecimal.ZERO.setScale(8));
        quote = builder.createQuote(player, new BigDecimal("2.50"), 1L);
        MarketP2PFixtureBuilder.FixtureIdempotency offerRequest = builder.createIdempotency(
                seller, "cancel-offer-" + seller.id(), "P2P_OFFER", "a".repeat(64));
        offer = builder.createOffer(seller, player, offerRequest, OFFER_QUANTITY);
        jdbc.update("UPDATE market_holdings SET reserved_quantity = ? "
                + "WHERE user_id = ? AND player_id = ?", OFFER_QUANTITY, seller.id(), player.id());
        jdbc.update("INSERT INTO market_state (id, status, epoch, created_at, updated_at) "
                + "VALUES (1, 'READY', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
                + "ON CONFLICT (id) DO UPDATE SET status = 'READY'");
    }

    @Test
    void cancelaOpenYLiberaExactamenteLaReserva() {
        MarketP2PCancelResult result = cancellations.cancelar(cancelCommand("open-key"));

        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(result.originalQuantity()).isEqualTo(OFFER_QUANTITY);
        assertThat(result.soldQuantity()).isZero();
        assertThat(result.releasedQuantity()).isEqualTo(OFFER_QUANTITY);
        assertOffer(0L, 0L, OFFER_QUANTITY, "CANCELLED");
        assertThat(reservedQuantity()).isZero();
        assertCancellationAudit(0L, OFFER_QUANTITY);
    }

    @Test
    void cancelaOfertaOperativamenteSuspendidaPorQuoteInvalidada() {
        jdbc.update("UPDATE market_quote_headers SET validity_status = 'INVALIDATED', "
                + "invalidated_at = CURRENT_TIMESTAMP, invalidation_reason = 'test' "
                + "WHERE player_id = ?", player.id());

        var suspended = offerQueries.consultar(new MarketP2POfferQuery(seller.id(),
                player.id(), "SUSPENDED", true, 1, 20));

        assertThat(suspended.offers()).singleElement()
                .satisfies(view -> {
                    assertThat(view.availability().status()).isEqualTo("SUSPENDED");
                    assertThat(view.availability().purchasable()).isFalse();
                });

        MarketP2PCancelResult result = cancellations.cancelar(cancelCommand("suspended-key"));

        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(result.releasedQuantity()).isEqualTo(OFFER_QUANTITY);
        assertOffer(0L, 0L, OFFER_QUANTITY, "CANCELLED");
        assertThat(reservedQuantity()).isZero();
        assertCancellationAudit(0L, OFFER_QUANTITY);
    }

    @Test
    void cancelaParcialSinRevertirTradesPrevios() {
        MarketP2PPurchaseResult purchase = purchase("partial-purchase-key", 1L);

        MarketP2PCancelResult result = cancellations.cancelar(cancelCommand("partial-key"));

        assertThat(result.originalQuantity()).isEqualTo(OFFER_QUANTITY);
        assertThat(result.soldQuantity()).isEqualTo(1L);
        assertThat(result.releasedQuantity()).isEqualTo(3L);
        assertOffer(0L, 1L, 3L, "CANCELLED");
        assertThat(reservedQuantity()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_trades "
                + "WHERE id = ?", Long.class, purchase.tradeId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT quantity FROM market_p2p_trades "
                + "WHERE id = ?", Long.class, purchase.tradeId())).isEqualTo(1L);
        assertCancellationAudit(1L, 3L);
    }

    @Test
    void replayNoCreaAuditoriaNiLiberaUnaSegundaVez() {
        MarketP2PCancelCommand command = cancelCommand("replay-key");

        MarketP2PCancelResult first = cancellations.cancelar(command);
        MarketP2PCancelResult replay = cancellations.cancelar(command);

        assertThat(replay).isEqualTo(first);
        assertThat(reservedQuantity()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_offer_cancellations "
                + "WHERE offer_id = ?", Long.class, offer.id())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_idempotency_requests "
                + "WHERE user_id = ? AND idempotency_key = ?", Long.class,
                seller.id(), "replay-key")).isEqualTo(1L);
    }

    @Test
    void rollbackRevierteOfertaReservaAuditoriaEIdempotencia() {
        Snapshot before = snapshot();
        installFailureTrigger();
        MarketP2PCancelCommand rollbackCommand = cancelCommand("rollback-key");
        try {
            assertThatThrownBy(() -> cancellations.cancelar(rollbackCommand))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            removeFailureTrigger();
        }

        assertThat(snapshot()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_offer_cancellations "
                + "WHERE offer_id = ?", Long.class, offer.id())).isZero();
    }

    @Test
    void dosCancelacionesConcurrentesLiberanUnaSolaVez() throws Exception {
        MarketP2PCancelCommand firstCancel = cancelCommand("concurrent-a");
        MarketP2PCancelCommand secondCancel = cancelCommand("concurrent-b");
        List<Outcome> outcomes = concurrently(
                () -> cancellations.cancelar(firstCancel),
                () -> cancellations.cancelar(secondCancel));

        assertThat(outcomes.stream().filter(Outcome::successful).count()).isEqualTo(1L);
        assertThat(outcomes.stream().filter(outcome -> !outcome.successful()).count())
                .isEqualTo(1L);
        assertOffer(0L, 0L, OFFER_QUANTITY, "CANCELLED");
        assertThat(reservedQuantity()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_offer_cancellations "
                + "WHERE offer_id = ?", Long.class, offer.id())).isEqualTo(1L);
    }

    @Test
    void compraYCancelacionConcurrentesConservanLaCantidadTotal() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(3);
        MarketP2PPurchaseCommand purchaseCommand = new MarketP2PPurchaseCommand(
                buyer.id(), "race-purchase-key", offer.id(), player.id(), OFFER_QUANTITY,
                new MarketP2PQuoteIdentity(quote.quoteId(), quote.version()));
        MarketP2PCancelCommand cancelCommand = this.cancelCommand("race-cancel-key");
        Callable<Outcome> purchase = () -> runOutcome(barrier,
                () -> purchases.comprar(purchaseCommand));
        Callable<Outcome> cancel = () -> runOutcome(barrier,
                () -> cancellations.cancelar(cancelCommand));

        var executor = Executors.newFixedThreadPool(2);
        try {
            var futures = List.of(executor.submit(purchase), executor.submit(cancel));
            barrier.await(20, TimeUnit.SECONDS);
            List<Outcome> outcomes = List.of(futures.get(0).get(30, TimeUnit.SECONDS),
                    futures.get(1).get(30, TimeUnit.SECONDS));
            assertThat(outcomes.stream().filter(Outcome::successful).count()).isEqualTo(1L);
            assertThat(outcomes.stream().filter(outcome -> !outcome.successful()).count())
                    .isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }

        long sold = jdbc.queryForObject("SELECT sold_quantity FROM market_p2p_offers "
                + "WHERE id = ?", Long.class, offer.id());
        long released = jdbc.queryForObject("SELECT released_quantity FROM market_p2p_offers "
                + "WHERE id = ?", Long.class, offer.id());
        assertThat(sold + released).isEqualTo(OFFER_QUANTITY);
        assertThat(jdbc.queryForObject("SELECT remaining_quantity FROM market_p2p_offers "
                + "WHERE id = ?", Long.class, offer.id())).isZero();
        assertThat(reservedQuantity()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_offer_cancellations "
                + "WHERE offer_id = ?", Long.class, offer.id())).isLessThanOrEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_trades "
                + "WHERE offer_id = ?", Long.class, offer.id())).isLessThanOrEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT quantity FROM market_holdings "
                + "WHERE user_id = ? AND player_id = ?", Long.class, buyer.id(), player.id()))
                .isEqualTo(sold);
    }

    @Test
    void auditoriaEsAppendOnly() {
        cancellations.cancelar(cancelCommand("immutable-key"));

        var offerId = offer.id();
        assertThatThrownBy(() -> jdbc.update("UPDATE market_p2p_offer_cancellations "
                + "SET released_quantity = 1 WHERE offer_id = ?", offerId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM market_p2p_offer_cancellations "
                + "WHERE offer_id = ?", offerId))
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_offer_cancellations "
                + "WHERE offer_id = ?", Long.class, offer.id())).isEqualTo(1L);
    }

    private MarketP2PCancelCommand cancelCommand(String key) {
        return new MarketP2PCancelCommand(seller.id(), offer.id(), key);
    }

    private MarketP2PPurchaseResult purchase(String key, long quantity) {
        return purchases.comprar(new MarketP2PPurchaseCommand(buyer.id(), key, offer.id(),
                player.id(), quantity,
                new MarketP2PQuoteIdentity(quote.quoteId(), quote.version())));
    }

    private void assertOffer(long remaining, long sold, long released, String status) {
        assertThat(jdbc.queryForObject("SELECT remaining_quantity FROM market_p2p_offers "
                + "WHERE id = ?", Long.class, offer.id())).isEqualTo(remaining);
        assertThat(jdbc.queryForObject("SELECT sold_quantity FROM market_p2p_offers "
                + "WHERE id = ?", Long.class, offer.id())).isEqualTo(sold);
        assertThat(jdbc.queryForObject("SELECT released_quantity FROM market_p2p_offers "
                + "WHERE id = ?", Long.class, offer.id())).isEqualTo(released);
        assertThat(jdbc.queryForObject("SELECT status FROM market_p2p_offers "
                + "WHERE id = ?", String.class, offer.id())).isEqualTo(status);
        assertThat(remaining + sold + released).isEqualTo(OFFER_QUANTITY);
    }

    private void assertCancellationAudit(long sold, long released) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_offer_cancellations "
                + "WHERE offer_id = ?", Long.class, offer.id())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT original_quantity FROM market_p2p_offer_cancellations "
                + "WHERE offer_id = ?", Long.class, offer.id())).isEqualTo(OFFER_QUANTITY);
        assertThat(jdbc.queryForObject("SELECT sold_quantity FROM market_p2p_offer_cancellations "
                + "WHERE offer_id = ?", Long.class, offer.id())).isEqualTo(sold);
        assertThat(jdbc.queryForObject("SELECT released_quantity FROM market_p2p_offer_cancellations "
                + "WHERE offer_id = ?", Long.class, offer.id())).isEqualTo(released);
    }

    private long reservedQuantity() {
        return jdbc.queryForObject("SELECT reserved_quantity FROM market_holdings "
                + "WHERE user_id = ? AND player_id = ?", Long.class, seller.id(), player.id());
    }

    private Snapshot snapshot() {
        return new Snapshot(new OfferState(
                jdbc.queryForObject("SELECT remaining_quantity FROM market_p2p_offers "
                        + "WHERE id = ?", Long.class, offer.id()),
                jdbc.queryForObject("SELECT sold_quantity FROM market_p2p_offers "
                        + "WHERE id = ?", Long.class, offer.id()),
                jdbc.queryForObject("SELECT released_quantity FROM market_p2p_offers "
                        + "WHERE id = ?", Long.class, offer.id()),
                jdbc.queryForObject("SELECT status FROM market_p2p_offers "
                        + "WHERE id = ?", String.class, offer.id())),
                reservedQuantity(),
                jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_offer_cancellations "
                        + "WHERE offer_id = ?", Long.class, offer.id()),
                jdbc.queryForObject("SELECT COUNT(*) FROM market_idempotency_requests "
                        + "WHERE user_id = ? AND idempotency_key = ?", Long.class,
                        seller.id(), "rollback-key"));
    }

    private List<Outcome> concurrently(Callable<MarketP2PCancelResult> first,
            Callable<MarketP2PCancelResult> second) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(3);
        Callable<Outcome> firstCall = () -> runOutcome(barrier, first);
        Callable<Outcome> secondCall = () -> runOutcome(barrier, second);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var futures = List.of(executor.submit(firstCall), executor.submit(secondCall));
            barrier.await(20, TimeUnit.SECONDS);
            return List.of(futures.get(0).get(30, TimeUnit.SECONDS),
                    futures.get(1).get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private Outcome runOutcome(CyclicBarrier barrier, Callable<?> operation) {
        try {
            barrier.await(20, TimeUnit.SECONDS);
            return new Outcome(true, operation.call());
        } catch (Exception exception) {
            return new Outcome(false, exception);
        }
    }

    private void installFailureTrigger() {
        jdbc.execute("CREATE OR REPLACE FUNCTION test_fail_p2p_cancellation() RETURNS trigger "
                + "LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced cancellation rollback'; "
                + "END; $$");
        jdbc.execute("CREATE TRIGGER test_fail_p2p_cancellation BEFORE INSERT ON "
                + "market_p2p_offer_cancellations FOR EACH ROW EXECUTE FUNCTION "
                + "test_fail_p2p_cancellation()");
    }

    private void removeFailureTrigger() {
        jdbc.execute("DROP TRIGGER IF EXISTS test_fail_p2p_cancellation "
                + "ON market_p2p_offer_cancellations");
        jdbc.execute("DROP FUNCTION IF EXISTS test_fail_p2p_cancellation()");
    }

    private record OfferState(long remaining, long sold, long released, String status) { }

    private record Snapshot(OfferState offer, long reserved, long audits, long idempotency) { }

    private record Outcome(boolean successful, Object value) { }
}
