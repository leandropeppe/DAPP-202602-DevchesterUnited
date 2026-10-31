package com.devchesterunited.devchesterunitedv2.integration.persistence;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PQuoteIdentity;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseCommand;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseResult;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PIdempotencyRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseIdentity;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketIdempotencyParameterHasher;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "token-market.initialization.auto-start=false")
/**
 * Shared T034 scenarios retained here while the executable test uses the SDD name.
 * Keeping the scenarios in one base prevents the atomicity suite from being run twice.
 */
abstract class MarketP2PPurchasePersistenceIntegrationTest extends PostgresTestSupport {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MarketP2PPurchaseService purchases;

    private MarketP2PFixtureBuilder.FixtureUser seller;
    private MarketP2PFixtureBuilder.FixtureUser buyer;
    private MarketP2PFixtureBuilder.FixtureUser buyerTwo;
    private MarketP2PFixtureBuilder.FixturePlayer player;
    private MarketP2PFixtureBuilder.FixtureQuote quote;
    private MarketP2PFixtureBuilder.FixtureOffer offer;

    @BeforeEach
    void fixture() {
        MarketP2PFixtureBuilder builder = new MarketP2PFixtureBuilder(jdbc);
        seller = builder.createUser("purchase-seller", BigDecimal.ZERO.setScale(2));
        buyer = builder.createUser("purchase-buyer", new BigDecimal("100.00"));
        buyerTwo = builder.createUser("purchase-buyer-two", new BigDecimal("100.00"));
        player = builder.createPlayer("P2P Purchase Player");
        builder.createHolding(seller, player, 10L, new BigDecimal("10.00000000"));
        builder.createHolding(buyer, player, 0L, BigDecimal.ZERO.setScale(8));
        builder.createHolding(buyerTwo, player, 0L, BigDecimal.ZERO.setScale(8));
        quote = builder.createQuote(player, new BigDecimal("2.50"), 1L);
        MarketP2PFixtureBuilder.FixtureIdempotency offerRequest = builder.createIdempotency(
                seller, "offer-" + seller.id(), "P2P_OFFER", "a".repeat(64));
        offer = builder.createOffer(seller, player, offerRequest, 4L);
        jdbc.update("UPDATE market_holdings SET reserved_quantity = 4 "
                + "WHERE user_id = ? AND player_id = ?", seller.id(), player.id());
        jdbc.update("INSERT INTO market_state (id, status, epoch, created_at, updated_at) "
                + "VALUES (1, 'READY', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
                + "ON CONFLICT (id) DO UPDATE SET status = 'READY'");
    }

    @Test
    void compraParcialTransfiereValorYReduceReserva() {
        MarketP2PPurchaseResult result = purchase("partial-key", 2L);

        assertThat(result.tradeId()).isNotNull();
        assertThat(result.settlement().quantities().remaining()).isEqualTo(2L);
        assertThat(result.settlement().quantities().reserved()).isEqualTo(2L);
        assertThat(result.settlement().quantities().offerStatus())
                .isEqualTo("PARTIALLY_FILLED");
        assertThat(jdbc.queryForObject("SELECT balance FROM market_credit_accounts "
                + "WHERE user_id = ?", BigDecimal.class, buyer.id()))
                .isEqualByComparingTo("95.00");
        assertThat(jdbc.queryForObject("SELECT balance FROM market_credit_accounts "
                + "WHERE user_id = ?", BigDecimal.class, seller.id()))
                .isEqualByComparingTo("5.00");
        assertThat(jdbc.queryForObject("SELECT quantity FROM market_holdings "
                + "WHERE user_id = ? AND player_id = ?", Long.class, seller.id(), player.id()))
                .isEqualTo(8L);
        assertThat(jdbc.queryForObject("SELECT acquisition_cost FROM market_holdings "
                + "WHERE user_id = ? AND player_id = ?", BigDecimal.class, seller.id(), player.id()))
                .isEqualByComparingTo("8.00000000");
        assertThat(jdbc.queryForObject("SELECT quantity FROM market_holdings "
                + "WHERE user_id = ? AND player_id = ?", Long.class, buyer.id(), player.id()))
                .isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_trades "
                + "WHERE offer_id = ?", Long.class, offer.id())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_trade_entries "
                + "WHERE trade_id = ?", Long.class, result.tradeId())).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_movements "
                + "WHERE trade_id = ?", Long.class, result.tradeId())).isEqualTo(4L);
        assertThat(jdbc.queryForObject("SELECT realized_pnl FROM market_p2p_trades "
                + "WHERE id = ?", BigDecimal.class, result.tradeId()))
                .isEqualByComparingTo("3.00000000");
        assertThat(jdbc.queryForObject("SELECT offer_status_after_trade FROM market_p2p_trades "
                + "WHERE id = ?", String.class, result.tradeId()))
                .isEqualTo("PARTIALLY_FILLED");
        assertThat(jdbc.queryForObject("SELECT remaining_quantity_after_trade FROM market_p2p_trades "
                + "WHERE id = ?", Long.class, result.tradeId())).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT reserved_quantity_after_trade FROM market_p2p_trades "
                + "WHERE id = ?", Long.class, result.tradeId())).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT realized_pnl FROM market_realized_pnl "
                + "WHERE user_id = ?", BigDecimal.class, seller.id()))
                .isEqualByComparingTo("3.00000000");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_movements "
                + "WHERE trade_id = ? AND movement_role IN "
                + "('BUYER_CREDIT_DEBIT', 'SELLER_TOKEN_DEBIT') AND quantity_delta < 0",
                Long.class, result.tradeId())).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_movements "
                + "WHERE trade_id = ? AND movement_role IN "
                + "('SELLER_CREDIT_CREDIT', 'BUYER_TOKEN_CREDIT') AND quantity_delta > 0",
                Long.class, result.tradeId())).isEqualTo(2L);
    }

    @Test
    void compraCompletaDejaOfertaYReservaEnCero() {
        MarketP2PPurchaseResult result = purchase("full-key", 4L);

        assertThat(result.settlement().quantities().remaining()).isZero();
        assertThat(result.settlement().quantities().reserved()).isZero();
        assertThat(result.settlement().quantities().offerStatus()).isEqualTo("FILLED");
        assertThat(jdbc.queryForObject("SELECT offer_status_after_trade "
                + "FROM market_p2p_trades WHERE id = ?", String.class, result.tradeId()))
                .isEqualTo("FILLED");
        assertThat(jdbc.queryForObject("SELECT remaining_quantity_after_trade "
                + "FROM market_p2p_trades WHERE id = ?", Long.class, result.tradeId()))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT reserved_quantity_after_trade "
                + "FROM market_p2p_trades WHERE id = ?", Long.class, result.tradeId()))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM market_p2p_offers "
                + "WHERE id = ?", String.class, offer.id())).isEqualTo("FILLED");
        assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM market_holdings "
                + "WHERE user_id = ? AND player_id = ?", Long.class, seller.id(), player.id()))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_movements "
                + "WHERE trade_id = (SELECT id FROM market_p2p_trades WHERE offer_id = ?)",
                Long.class, offer.id())).isEqualTo(4L);
        assertThat(jdbc.queryForObject("SELECT realized_pnl FROM market_realized_pnl "
                + "WHERE user_id = ?", BigDecimal.class, seller.id()))
                .isEqualByComparingTo("6.00000000");
    }

    @Test
    void replayEsDurableYSaldoInsuficienteNoMuta() {
        MarketP2PPurchaseResult first = purchase("replay-key", 1L);
        MarketP2PPurchaseResult replay = purchase("replay-key", 1L);

        assertThat(replay.tradeId()).isEqualTo(first.tradeId());
        assertThat(replay).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_trades "
                + "WHERE offer_id = ?", Long.class, offer.id())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_movements "
                + "WHERE trade_id = ?", Long.class, first.tradeId())).isEqualTo(4L);
        assertThat(jdbc.queryForObject("SELECT realized_pnl FROM market_realized_pnl "
                + "WHERE user_id = ?", BigDecimal.class, seller.id()))
                .isEqualByComparingTo("1.50000000");

        MarketP2PPurchaseResult later = purchase("replay-later-key", 1L);
        assertThat(later.tradeId()).isNotEqualTo(first.tradeId());
        MarketP2PPurchaseResult replayAfterLater = purchase("replay-key", 1L);
        assertThat(replayAfterLater).isEqualTo(first);

        jdbc.update("UPDATE market_credit_accounts SET balance = 1 "
                + "WHERE user_id = ?", buyer.id());
        assertThatThrownBy(() -> purchase("insufficient-key", 1L))
                .isInstanceOfSatisfying(TokenMarketException.class, exception ->
                        assertThat(exception.reason())
                                .isEqualTo(TokenMarketException.Reason.INSUFFICIENT_BALANCE));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_trades "
                + "WHERE offer_id = ?", Long.class, offer.id())).isEqualTo(2L);
    }

    @Test
    void replayConservaSnapshotTrasCancelacionPosterior() {
        MarketP2PPurchaseResult first = purchase("replay-cancel-key", 1L);
        jdbc.update("UPDATE market_p2p_offers SET remaining_quantity = 0, "
                + "released_quantity = 3, status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP "
                + "WHERE id = ?", offer.id());
        jdbc.update("UPDATE market_holdings SET reserved_quantity = 0 "
                + "WHERE user_id = ? AND player_id = ?", seller.id(), player.id());

        MarketP2PPurchaseResult replay = purchase("replay-cancel-key", 1L);
        assertThat(replay).isEqualTo(first);
        assertThat(replay.settlement().quantities().offerStatus())
                .isEqualTo("PARTIALLY_FILLED");
        assertThat(replay.settlement().quantities().remaining()).isEqualTo(3L);
        assertThat(replay.settlement().quantities().reserved()).isEqualTo(3L);
    }

    @Test
    void replayLegacySinSnapshotEsConflictoExplicito() {
        String key = "legacy-replay-key";
        MarketP2PIdempotencyRequest request = new MarketP2PIdempotencyRequest(
                buyer.id(), key, new MarketP2PPurchaseIdentity(offer.id(), player.id(), 1L,
                new MarketP2PQuoteIdentity(quote.quoteId(), quote.version())));
        String hash = MarketIdempotencyParameterHasher.hash(request);
        MarketP2PFixtureBuilder builder = new MarketP2PFixtureBuilder(jdbc);
        MarketP2PFixtureBuilder.FixtureIdempotency idempotency = builder.createIdempotency(
                buyer, key, "P2P_PURCHASE", hash);
        UUID tradeId = UUID.randomUUID();
        jdbc.update("UPDATE market_idempotency_requests SET status = 'CONFIRMED', "
                + "execution_token = NULL, result_reference = ?, "
                + "updated_at = GREATEST(created_at, CURRENT_TIMESTAMP) "
                + "WHERE id = ?", tradeId, idempotency.id());
        jdbc.update("INSERT INTO market_p2p_trades "
                + "(id, offer_id, seller_user_id, buyer_user_id, player_id, "
                + "player_name_snapshot, quantity, unit_price, total_amount, quote_id, "
                + "quote_version, idempotency_request_id, confirmed_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                tradeId, offer.id(), seller.id(), buyer.id(), player.id(), player.name(),
                quote.price(), quote.price(), quote.quoteId(), quote.version(), idempotency.id());

        assertThatThrownBy(() -> purchase(key, 1L))
                .isInstanceOfSatisfying(TokenMarketException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(
                                TokenMarketException.Reason.HISTORICAL_RESULT_UNAVAILABLE));
    }

    @Test
    void falloDespuesDeMutacionesRevierteTodaLaLiquidacion() {
        Snapshot before = snapshot();
        installFailureTrigger();
        try {
            assertThatThrownBy(() -> purchase("forced-rollback-key", 2L))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            removeFailureTrigger();
        }

        Snapshot after = snapshot();
        assertThat(after).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_idempotency_requests "
                + "WHERE user_id = ? AND idempotency_key = ?", Long.class,
                buyer.id(), "forced-rollback-key")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_trades "
                + "WHERE offer_id = ?", Long.class, offer.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_trade_entries "
                + "WHERE trade_id NOT IN (SELECT id FROM market_p2p_trades)", Long.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_movements "
                + "WHERE trade_id IN (SELECT id FROM market_p2p_trades "
                + "WHERE offer_id = ?)", Long.class, offer.id())).isZero();
    }

    @Test
    void rechazaOfertaPropiaYCantidadSuperiorSinMutar() {
        assertThatThrownBy(() -> purchases.comprar(new MarketP2PPurchaseCommand(
                seller.id(), "own-key", offer.id(), player.id(), 1L,
                new MarketP2PQuoteIdentity(quote.quoteId(), quote.version()))))
                .isInstanceOfSatisfying(TokenMarketException.class, exception ->
                        assertThat(exception.reason())
                                .isEqualTo(TokenMarketException.Reason.OFFER_NOT_PURCHASABLE));
        assertThatThrownBy(() -> purchase("too-many-key", 5L))
                .isInstanceOfSatisfying(TokenMarketException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(
                                TokenMarketException.Reason.INSUFFICIENT_AVAILABLE_QUANTITY));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_trades "
                + "WHERE offer_id = ?", Long.class, offer.id())).isZero();
    }

    @Test
    void cotizacionCambiadaNoLiquidaLaOferta() {
        UUID changedQuoteId = UUID.randomUUID();
        jdbc.update("INSERT INTO market_quote_versions "
                        + "(quote_id, version, player_id, price_per_token, published_at, "
                        + "valid_from, valid_until, validity_status, strategy_reference, created_at) "
                        + "VALUES (?, 2, ?, 3.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL, "
                        + "'VALID', 'P2P_TEST', CURRENT_TIMESTAMP)",
                changedQuoteId, player.id());
        jdbc.update("UPDATE market_quote_headers SET quote_id = ?, current_version = 2 "
                + "WHERE player_id = ?", changedQuoteId, player.id());

        assertThatThrownBy(() -> purchase("changed-quote-key", 1L))
                .isInstanceOfSatisfying(TokenMarketException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(
                                TokenMarketException.Reason.QUOTE_CHANGED));
        assertThat(jdbc.queryForObject("SELECT remaining_quantity FROM market_p2p_offers "
                + "WHERE id = ?", Long.class, offer.id())).isEqualTo(4L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_trades "
                + "WHERE offer_id = ?", Long.class, offer.id())).isZero();
    }

    @Test
    void compradoresConcurrentesNoSuperanElRemanente() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<MarketP2PPurchaseResult> first = concurrentPurchase(barrier, buyer,
                "concurrent-one");
        Callable<MarketP2PPurchaseResult> second = concurrentPurchase(barrier, buyerTwo,
                "concurrent-two");
        var executor = Executors.newFixedThreadPool(2);
        try {
            List<java.util.concurrent.Future<MarketP2PPurchaseResult>> results = executor
                    .invokeAll(List.of(first, second), 30, TimeUnit.SECONDS);
            long successful = results.stream().filter(this::isSuccessful).count();
            assertThat(successful).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT remaining_quantity FROM market_p2p_offers "
                + "WHERE id = ?", Long.class, offer.id())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM market_holdings "
                + "WHERE user_id = ? AND player_id = ?", Long.class, seller.id(), player.id()))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_trades "
                + "WHERE offer_id = ?", Long.class, offer.id())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_movements "
                + "WHERE trade_id IN (SELECT id FROM market_p2p_trades WHERE offer_id = ?)",
                Long.class, offer.id())).isEqualTo(4L);
        assertThat(jdbc.queryForObject("SELECT realized_pnl FROM market_realized_pnl "
                + "WHERE user_id = ?", BigDecimal.class, seller.id()))
                .isEqualByComparingTo("4.50000000");
        assertThat(jdbc.queryForObject("SELECT COALESCE(SUM(quantity), 0) "
                + "FROM market_holdings WHERE user_id IN (?, ?)", Long.class,
                buyer.id(), buyerTwo.id())).isEqualTo(3L);
    }

    private MarketP2PPurchaseResult purchase(String key, long quantity) {
        return purchases.comprar(new MarketP2PPurchaseCommand(buyer.id(), key, offer.id(),
                player.id(), quantity, new MarketP2PQuoteIdentity(quote.quoteId(), quote.version())));
    }

    private Callable<MarketP2PPurchaseResult> concurrentPurchase(CyclicBarrier barrier,
            MarketP2PFixtureBuilder.FixtureUser user, String key) {
        return () -> {
            barrier.await(20, TimeUnit.SECONDS);
            return purchases.comprar(new MarketP2PPurchaseCommand(user.id(), key, offer.id(),
                    player.id(), 3L,
                    new MarketP2PQuoteIdentity(quote.quoteId(), quote.version())));
        };
    }

    private boolean isSuccessful(java.util.concurrent.Future<MarketP2PPurchaseResult> future) {
        try {
            return future.get().tradeId() != null;
        } catch (Exception exception) {
            return false;
        }
    }

    private Snapshot snapshot() {
        Balances balances = new Balances(
                jdbc.queryForObject("SELECT balance FROM market_credit_accounts "
                        + "WHERE user_id = ?", BigDecimal.class, buyer.id()),
                jdbc.queryForObject("SELECT balance FROM market_credit_accounts "
                        + "WHERE user_id = ?", BigDecimal.class, seller.id()));
        Holdings holdings = new Holdings(
                jdbc.queryForObject("SELECT quantity FROM market_holdings "
                        + "WHERE user_id = ? AND player_id = ?", Long.class, seller.id(), player.id()),
                jdbc.queryForObject("SELECT reserved_quantity FROM market_holdings "
                        + "WHERE user_id = ? AND player_id = ?", Long.class, seller.id(), player.id()),
                jdbc.queryForObject("SELECT quantity FROM market_holdings "
                        + "WHERE user_id = ? AND player_id = ?", Long.class, buyer.id(), player.id()),
                jdbc.queryForObject("SELECT acquisition_cost FROM market_holdings "
                        + "WHERE user_id = ? AND player_id = ?", BigDecimal.class, buyer.id(), player.id()));
        OfferState offerState = new OfferState(
                jdbc.queryForObject("SELECT remaining_quantity FROM market_p2p_offers "
                        + "WHERE id = ?", Long.class, offer.id()),
                jdbc.queryForObject("SELECT status FROM market_p2p_offers "
                        + "WHERE id = ?", String.class, offer.id()));
        Records records = new Records(
                jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_trades "
                        + "WHERE offer_id = ?", Long.class, offer.id()),
                jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_trade_entries "
                        + "WHERE trade_id IN (SELECT id FROM market_p2p_trades WHERE offer_id = ?)",
                        Long.class, offer.id()),
                jdbc.queryForObject("SELECT COUNT(*) FROM market_movements "
                        + "WHERE player_id = ?", Long.class, player.id()),
                jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_movements "
                        + "WHERE player_id = ?", Long.class, player.id()),
                jdbc.queryForObject("SELECT COALESCE(SUM(realized_pnl), 0) "
                        + "FROM market_realized_pnl WHERE user_id = ?", BigDecimal.class,
                        seller.id()));
        return new Snapshot(balances, holdings, offerState, records);
    }

    private void installFailureTrigger() {
        jdbc.execute("CREATE OR REPLACE FUNCTION test_fail_p2p_entry() RETURNS trigger "
                + "LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced test rollback'; "
                + "END; $$");
        jdbc.execute("CREATE TRIGGER trg_test_fail_p2p_entry "
                + "BEFORE INSERT ON market_p2p_trade_entries "
                + "FOR EACH ROW EXECUTE FUNCTION test_fail_p2p_entry()");
    }

    private void removeFailureTrigger() {
        jdbc.execute("DROP TRIGGER IF EXISTS trg_test_fail_p2p_entry "
                + "ON market_p2p_trade_entries");
        jdbc.execute("DROP FUNCTION IF EXISTS test_fail_p2p_entry()");
    }

    private record Snapshot(Balances balances, Holdings holdings, OfferState offer,
            Records records) { }

    private record Balances(BigDecimal buyer, BigDecimal seller) { }

    private record Holdings(Long sellerQuantity, Long sellerReserved, Long buyerQuantity,
            BigDecimal buyerAcquisitionCost) { }

    private record OfferState(Long remaining, String status) { }

    private record Records(Long trades, Long entries, Long marketMovements,
            Long p2pMovements, BigDecimal realizedPnl) { }
}
