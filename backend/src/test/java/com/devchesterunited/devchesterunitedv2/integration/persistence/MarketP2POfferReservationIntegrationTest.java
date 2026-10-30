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
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "token-market.initialization.auto-start=false")
class MarketP2POfferReservationIntegrationTest extends PostgresTestSupport {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MarketP2POfferService offers;

    private MarketP2PFixtureBuilder.FixtureUser seller;
    private MarketP2PFixtureBuilder.FixturePlayer player;
    private MarketP2PFixtureBuilder.FixtureQuote quote;

    @BeforeEach
    void fixture() {
        MarketP2PFixtureBuilder builder = new MarketP2PFixtureBuilder(jdbc);
        seller = builder.createUser("reservation", new BigDecimal("100.00"));
        player = builder.createPlayer("P2P Reservation Player");
        builder.createHolding(seller, player, 10L, new BigDecimal("10.00000000"));
        quote = builder.createQuote(player, new BigDecimal("2.50"), 1L);
        jdbc.update("INSERT INTO market_state (id, status, epoch, created_at, updated_at) "
                + "VALUES (1, 'READY', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
                + "ON CONFLICT (id) DO UPDATE SET status = 'READY'");
    }

    @Test
    void publicaReservaInmediatamenteYReplayNoCreaOtraOferta() {
        MarketP2POfferCommand command = command("reservation-key", 4L);

        MarketP2POfferResult first = offers.publicar(command);
        MarketP2POfferResult replay = offers.publicar(command);

        assertThat(first.offerId()).isNotNull();
        assertThat(replay.offerId()).isEqualTo(first.offerId());
        assertThat(replay.status()).isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM market_holdings "
                + "WHERE user_id = ? AND player_id = ?", Long.class, seller.id(), player.id()))
                .isEqualTo(4L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_offers "
                + "WHERE seller_user_id = ? AND player_id = ?", Long.class, seller.id(), player.id()))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT SUM(remaining_quantity) FROM market_p2p_offers "
                + "WHERE seller_user_id = ? AND player_id = ? AND status IN ('OPEN', 'PARTIALLY_FILLED')",
                Long.class, seller.id(), player.id())).isEqualTo(4L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_offers "
                + "WHERE seller_user_id = ? AND player_id = ? AND created_at <= updated_at",
                Long.class, seller.id(), player.id())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_movements "
                + "WHERE token_user_id = ? OR credit_account_id IN "
                + "(SELECT id FROM market_credit_accounts WHERE user_id = ?)",
                Long.class, seller.id(), seller.id())).isZero();
    }

    @Test
    void noPermiteReservarMasQueLaDisponibilidadSinMutar() {
        offers.publicar(command("first-key", 7L));

        assertThatThrownBy(() -> offers.publicar(command("second-key", 4L)))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(TokenMarketException.Reason.INSUFFICIENT_AVAILABLE_QUANTITY));

        assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM market_holdings "
                + "WHERE user_id = ? AND player_id = ?", Long.class, seller.id(), player.id()))
                .isEqualTo(7L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_offers "
                + "WHERE seller_user_id = ? AND player_id = ?", Long.class, seller.id(), player.id()))
                .isEqualTo(1L);
    }

    @Test
    void jugadorInactivoNoReservaNiCreaOferta() {
        jdbc.update("UPDATE jugadores SET is_active = FALSE WHERE id = ?", player.id());

        assertThatThrownBy(() -> offers.publicar(command("inactive-key", 2L)))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(TokenMarketException.Reason.INACTIVE_PLAYER));

        assertThat(reservedQuantity()).isZero();
        assertThat(offerCount()).isZero();
    }

    @Test
    void cotizacionNoVigenteNoReservaNiCreaOferta() {
        jdbc.update("UPDATE market_quote_headers SET validity_status = 'INVALIDATED', "
                + "invalidated_at = CURRENT_TIMESTAMP, invalidation_reason = ? "
                + "WHERE player_id = ?", "test-invalidated", player.id());

        assertThatThrownBy(() -> offers.publicar(command("invalid-quote-key", 2L)))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(TokenMarketException.Reason.QUOTE_UNAVAILABLE));

        assertThat(reservedQuantity()).isZero();
        assertThat(offerCount()).isZero();
    }

    @Test
    void publicacionesConcurrentesNoSuperanLaDisponibilidad() throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        Callable<MarketP2POfferResult> first = concurrentCall(start, "concurrent-a");
        Callable<MarketP2POfferResult> second = concurrentCall(start, "concurrent-b");
        var executor = Executors.newFixedThreadPool(2);
        try {
            List<java.util.concurrent.Future<MarketP2POfferResult>> results = executor.invokeAll(
                    List.of(first, second), 30, TimeUnit.SECONDS);
            long successes = results.stream().filter(this::isSuccessful).count();
            assertThat(successes).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM market_holdings "
                + "WHERE user_id = ? AND player_id = ?", Long.class, seller.id(), player.id()))
                .isEqualTo(6L);
        assertThat(jdbc.queryForObject("SELECT SUM(remaining_quantity) FROM market_p2p_offers "
                + "WHERE seller_user_id = ? AND player_id = ? AND status IN ('OPEN', 'PARTIALLY_FILLED')",
                Long.class, seller.id(), player.id())).isEqualTo(6L);
    }

    private Callable<MarketP2POfferResult> concurrentCall(CyclicBarrier start, String key) {
        return () -> {
            await(start);
            return offers.publicar(command(key, 6L));
        };
    }

    private boolean isSuccessful(java.util.concurrent.Future<MarketP2POfferResult> future) {
        try {
            return future.get().offerId() != null;
        } catch (Exception exception) {
            return false;
        }
    }

    private MarketP2POfferCommand command(String key, long quantity) {
        return new MarketP2POfferCommand(seller.id(), player.id(), quantity, key);
    }

    private long reservedQuantity() {
        return jdbc.queryForObject("SELECT reserved_quantity FROM market_holdings "
                + "WHERE user_id = ? AND player_id = ?", Long.class, seller.id(), player.id());
    }

    private long offerCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM market_p2p_offers "
                + "WHERE seller_user_id = ? AND player_id = ?", Long.class, seller.id(), player.id());
    }

    private static void await(CyclicBarrier barrier) throws Exception {
        barrier.await(20, TimeUnit.SECONDS);
    }
}
