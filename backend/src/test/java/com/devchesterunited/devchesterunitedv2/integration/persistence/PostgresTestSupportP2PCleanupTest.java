package com.devchesterunited.devchesterunitedv2.integration.persistence;

import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.support.fixtures.market.MarketP2PFixtureBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "token-market.initialization.auto-start=false")
class PostgresTestSupportP2PCleanupTest extends PostgresTestSupport {
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void limpiaDependenciasP2PSinAfectarOtraFixtureYConservaTriggers() {
        MarketP2PFixtureBuilder builder = new MarketP2PFixtureBuilder(jdbc);
        var seller = builder.createUser("cleanup-seller", new BigDecimal("100.00"));
        var buyer = builder.createUser("cleanup-buyer", new BigDecimal("100.00"));
        var otherSeller = builder.createUser("cleanup-other", new BigDecimal("100.00"));
        var player = builder.createPlayer("Cleanup Player");
        var otherPlayer = builder.createPlayer("Other Cleanup Player");
        builder.createHolding(seller, player, 4L, new BigDecimal("4.00000000"));
        builder.createHolding(buyer, player, 0L, BigDecimal.ZERO.setScale(8));
        builder.createHolding(otherSeller, otherPlayer, 2L, new BigDecimal("2.00000000"));
        var quote = builder.createQuote(player, new BigDecimal("2.00"), 1L);

        try {
            var offerRequest = builder.createIdempotency(seller, "cleanup-offer", "P2P_OFFER", "a".repeat(64));
            var offer = builder.createOffer(seller, player, offerRequest, 2L);
            jdbc.update("UPDATE market_p2p_offers SET status = 'PARTIALLY_FILLED', remaining_quantity = 1, sold_quantity = 1 WHERE id = ?", offer.id());
            var tradeRequest = builder.createIdempotency(buyer, "cleanup-trade", "P2P_PURCHASE", "b".repeat(64));
            var tradeId = insertTrade(offer, seller, buyer, player, quote, tradeRequest);
            insertTradeDetails(tradeId, seller, buyer, player);
            jdbc.update("UPDATE market_idempotency_requests SET status = 'CONFIRMED', execution_token = NULL, result_reference = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", tradeId, tradeRequest.id());

            var cancellationRequest = builder.createIdempotency(seller, "cleanup-cancel", "P2P_CANCEL", "c".repeat(64));
            var cancelledOffer = builder.createOffer(seller, player, cancellationRequest, 1L);
            jdbc.update("UPDATE market_p2p_offers SET status = 'CANCELLED', remaining_quantity = 0, released_quantity = 1 WHERE id = ?", cancelledOffer.id());
            jdbc.update("INSERT INTO market_p2p_offer_cancellations "
                    + "(id, offer_id, actor_user_id, original_quantity, sold_quantity, released_quantity, idempotency_request_id, cancelled_at) "
                    + "VALUES (?, ?, ?, 1, 0, 1, ?, CURRENT_TIMESTAMP)", UUID.randomUUID(), cancelledOffer.id(), seller.id(), cancellationRequest.id());

            var otherRequest = builder.createIdempotency(otherSeller, "cleanup-other-offer", "P2P_OFFER", "d".repeat(64));
            var otherOffer = builder.createOffer(otherSeller, otherPlayer, otherRequest, 1L);

            limpiarJugadoresYDependencias(jdbc, player.id());

            assertThat(count("SELECT COUNT(*) FROM jugadores WHERE id = ?", player.id())).isZero();
            assertThat(count("SELECT COUNT(*) FROM market_p2p_offers WHERE player_id = ?", player.id())).isZero();
            assertThat(count("SELECT COUNT(*) FROM market_p2p_trades WHERE player_id = ?", player.id())).isZero();
            assertThat(count("SELECT COUNT(*) FROM market_p2p_trade_entries WHERE trade_id = ?", tradeId)).isZero();
            assertThat(count("SELECT COUNT(*) FROM market_p2p_movements WHERE player_id = ?", player.id())).isZero();
            assertThat(count("SELECT COUNT(*) FROM market_p2p_offer_cancellations WHERE offer_id = ?", cancelledOffer.id())).isZero();
            assertThat(count("SELECT COUNT(*) FROM market_p2p_offers WHERE id = ?", otherOffer.id())).isEqualTo(1L);
            assertThat(count("SELECT COUNT(*) FROM jugadores WHERE id = ?", otherPlayer.id())).isEqualTo(1L);

            limpiarJugadoresYDependencias(jdbc, player.id());
            assertThat(count("SELECT COUNT(*) FROM market_p2p_offers WHERE id = ?", otherOffer.id())).isEqualTo(1L);
            assertThat(count("SELECT COUNT(*) FROM pg_trigger WHERE tgname IN ('trg_market_p2p_movements_immutable', 'trg_market_p2p_trade_entries_immutable', 'trg_market_p2p_cancellations_immutable', 'trg_market_p2p_trades_immutable') AND tgenabled = 'D'")).isZero();
        } finally {
            limpiarJugadoresYDependencias(jdbc, player.id(), otherPlayer.id());
            limpiarUsuarioYDependencias(jdbc, seller.id());
            limpiarUsuarioYDependencias(jdbc, buyer.id());
            limpiarUsuarioYDependencias(jdbc, otherSeller.id());
        }
    }

    private UUID insertTrade(MarketP2PFixtureBuilder.FixtureOffer offer,
            MarketP2PFixtureBuilder.FixtureUser seller, MarketP2PFixtureBuilder.FixtureUser buyer,
            MarketP2PFixtureBuilder.FixturePlayer player, MarketP2PFixtureBuilder.FixtureQuote quote,
            MarketP2PFixtureBuilder.FixtureIdempotency request) {
        UUID tradeId = UUID.randomUUID();
        jdbc.update("INSERT INTO market_p2p_trades "
                        + "(id, offer_id, seller_user_id, buyer_user_id, player_id, player_name_snapshot, quantity, unit_price, total_amount, quote_id, quote_version, idempotency_request_id, confirmed_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                tradeId, offer.id(), seller.id(), buyer.id(), player.id(), player.name(), quote.price(), quote.price(), quote.quoteId(), quote.version(), request.id());
        return tradeId;
    }

    private void insertTradeDetails(UUID tradeId, MarketP2PFixtureBuilder.FixtureUser seller,
            MarketP2PFixtureBuilder.FixtureUser buyer, MarketP2PFixtureBuilder.FixturePlayer player) {
        jdbc.update("INSERT INTO market_p2p_trade_entries (id, trade_id, user_id, entry_role, quantity, amount, created_at) VALUES (?, ?, ?, 'P2P_SALE', 1, 2, CURRENT_TIMESTAMP), (?, ?, ?, 'P2P_PURCHASE', 1, 2, CURRENT_TIMESTAMP)", UUID.randomUUID(), tradeId, seller.id(), UUID.randomUUID(), tradeId, buyer.id());
        jdbc.update("INSERT INTO market_p2p_movements (id, trade_id, movement_role, asset_type, credit_account_id, quantity_delta, created_at) VALUES (?, ?, 'BUYER_CREDIT_DEBIT', 'CREDIT', ?, -2, CURRENT_TIMESTAMP), (?, ?, 'SELLER_CREDIT_CREDIT', 'CREDIT', ?, 2, CURRENT_TIMESTAMP)", UUID.randomUUID(), tradeId, buyer.accountId(), UUID.randomUUID(), tradeId, seller.accountId());
        jdbc.update("INSERT INTO market_p2p_movements (id, trade_id, movement_role, asset_type, token_user_id, token_counterparty_user_id, player_id, quantity_delta, created_at) VALUES (?, ?, 'SELLER_TOKEN_DEBIT', 'TOKEN', ?, ?, ?, -1, CURRENT_TIMESTAMP), (?, ?, 'BUYER_TOKEN_CREDIT', 'TOKEN', ?, ?, ?, 1, CURRENT_TIMESTAMP)", UUID.randomUUID(), tradeId, seller.id(), buyer.id(), player.id(), UUID.randomUUID(), tradeId, buyer.id(), seller.id(), player.id());
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
