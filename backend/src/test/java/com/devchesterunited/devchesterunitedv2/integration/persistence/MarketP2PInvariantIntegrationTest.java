package com.devchesterunited.devchesterunitedv2.integration.persistence;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PCancelCommand;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PPurchaseCommand;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2PQuoteIdentity;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2POfferCommand;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2PCancellationService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2POfferService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2PPurchaseService;
import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.support.fixtures.market.MarketP2PFixtureBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "token-market.initialization.auto-start=false")
class MarketP2PInvariantIntegrationTest extends PostgresTestSupport {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MarketP2POfferService offers;

    @Autowired
    private MarketP2PPurchaseService purchases;

    @Autowired
    private MarketP2PCancellationService cancellations;

    private MarketP2PFixtureBuilder.FixtureUser seller;
    private MarketP2PFixtureBuilder.FixtureUser buyer;
    private MarketP2PFixtureBuilder.FixturePlayer player;
    private MarketP2PFixtureBuilder.FixtureQuote quote;

    @BeforeEach
    void fixture() {
        MarketP2PFixtureBuilder builder = new MarketP2PFixtureBuilder(jdbc);
        seller = builder.createUser("invariant-seller", BigDecimal.ZERO.setScale(2));
        buyer = builder.createUser("invariant-buyer", new BigDecimal("100.00"));
        player = builder.createPlayer("P2P Invariant Player");
        builder.createHolding(seller, player, 4L, new BigDecimal("4.00000000"));
        builder.createHolding(buyer, player, 0L, BigDecimal.ZERO.setScale(8));
        quote = builder.createQuote(player, new BigDecimal("2.50"), 1L);
        jdbc.update("INSERT INTO market_state (id, status, epoch, created_at, updated_at) "
                + "VALUES (1, 'READY', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
                + "ON CONFLICT (id) DO UPDATE SET status = 'READY'");
    }

    @Test
    void publicarComprarYCancelarConservanTodasLasInvariantes() {
        var published = offers.publicar(new MarketP2POfferCommand(seller.id(), player.id(),
                3L, "invariant-offer-key"));
        assertInvariants();
        assertThat(reservedQuantity(seller)).isEqualTo(3L);

        purchases.comprar(new MarketP2PPurchaseCommand(buyer.id(), "invariant-buy-key",
                published.offerId(), player.id(), 1L,
                new MarketP2PQuoteIdentity(quote.quoteId(), quote.version())));
        assertInvariants();
        assertThat(reservedQuantity(seller)).isEqualTo(2L);

        cancellations.cancelar(new MarketP2PCancelCommand(seller.id(), published.offerId(),
                "invariant-cancel-key"));
        assertInvariants();
        assertThat(reservedQuantity(seller)).isZero();
        assertThat(jdbc.queryForObject("SELECT remaining_quantity FROM market_p2p_offers "
                + "WHERE id = ?", Long.class, published.offerId())).isZero();
    }

    @Test
    void rollbackDeLiquidacionNoDejaInvariantesParciales() {
        var published = offers.publicar(new MarketP2POfferCommand(seller.id(), player.id(),
                2L, "invariant-rollback-offer"));
        Snapshot before = snapshot(published.offerId());
        installFailureTrigger();
        try {
            assertThatThrownBy(() -> purchases.comprar(new MarketP2PPurchaseCommand(
                    buyer.id(), "invariant-rollback-buy", published.offerId(), player.id(), 1L,
                    new MarketP2PQuoteIdentity(quote.quoteId(), quote.version()))))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            removeFailureTrigger();
        }
        assertThat(snapshot(published.offerId())).isEqualTo(before);
        assertInvariants();
    }

    @Test
    void checksSqlRechazanValoresNegativosOSobreReserva() {
        var published = offers.publicar(new MarketP2POfferCommand(seller.id(), player.id(),
                2L, "invariant-check-offer"));
        assertThatThrownBy(() -> jdbc.update("UPDATE market_credit_accounts SET balance = -1 "
                + "WHERE user_id = ?", seller.id())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE market_holdings SET reserved_quantity = 5 "
                + "WHERE user_id = ? AND player_id = ?", seller.id(), player.id()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE market_p2p_offers SET remaining_quantity = -1 "
                + "WHERE id = ?", published.offerId())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE market_holdings SET quantity = -1 "
                + "WHERE user_id = ? AND player_id = ?", seller.id(), player.id()))
                .isInstanceOf(DataAccessException.class);
        assertInvariants();
    }

    private void assertInvariants() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_credit_accounts "
                + "WHERE balance < 0", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_holdings "
                + "WHERE quantity < 0 OR reserved_quantity < 0 OR reserved_quantity > quantity",
                Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_token_issuances "
                + "WHERE inventory_quantity < 0 OR inventory_quantity > issued_quantity", Long.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_offers "
                + "WHERE remaining_quantity < 0 OR sold_quantity < 0 OR released_quantity < 0 "
                + "OR original_quantity <> remaining_quantity + sold_quantity + released_quantity",
                Long.class)).isZero();
    }

    private long reservedQuantity(MarketP2PFixtureBuilder.FixtureUser user) {
        return jdbc.queryForObject("SELECT reserved_quantity FROM market_holdings "
                + "WHERE user_id = ? AND player_id = ?", Long.class, user.id(), player.id());
    }

    private Snapshot snapshot(UUID offerId) {
        return new Snapshot(
                jdbc.queryForObject("SELECT remaining_quantity FROM market_p2p_offers WHERE id = ?",
                        Long.class, offerId),
                reservedQuantity(seller),
                jdbc.queryForObject("SELECT balance FROM market_credit_accounts WHERE user_id = ?",
                        BigDecimal.class, buyer.id()),
                jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_trades WHERE offer_id = ?",
                        Long.class, offerId),
                jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_movements "
                        + "WHERE trade_id IN (SELECT id FROM market_p2p_trades WHERE offer_id = ?)",
                        Long.class, offerId));
    }

    private void installFailureTrigger() {
        jdbc.execute("CREATE OR REPLACE FUNCTION test_fail_p2p_invariant() RETURNS trigger "
                + "LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced invariant rollback'; "
                + "END; $$");
        jdbc.execute("CREATE TRIGGER test_fail_p2p_invariant BEFORE INSERT ON "
                + "market_p2p_movements FOR EACH ROW EXECUTE FUNCTION test_fail_p2p_invariant()");
    }

    private void removeFailureTrigger() {
        jdbc.execute("DROP TRIGGER IF EXISTS test_fail_p2p_invariant ON market_p2p_movements");
        jdbc.execute("DROP FUNCTION IF EXISTS test_fail_p2p_invariant()");
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
        removeFailureTrigger();
        cleanup();
    }

    private record Snapshot(long remaining, long reserved, BigDecimal buyerBalance,
            long trades, long movements) { }
}
