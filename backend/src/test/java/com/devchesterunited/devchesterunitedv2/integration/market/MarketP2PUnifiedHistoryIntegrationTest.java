package com.devchesterunited.devchesterunitedv2.integration.market;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketTransactionFilter;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketTransactionService;
import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.support.fixtures.market.MarketP2PFixtureBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** T063: integration contract for unified legacy and P2P history. */
@SpringBootTest(properties = "token-market.initialization.auto-start=false")
class MarketP2PUnifiedHistoryIntegrationTest extends PostgresTestSupport {
    private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant FIXTURE_TIME = Instant.parse("2026-10-30T12:00:00Z");
    private static final UUID TRADE_SALE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TRADE_PURCHASE = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID ENTRY_SALE = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID ENTRY_PURCHASE = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID LEGACY_PURCHASE = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID LEGACY_SALE = UUID.fromString("30000000-0000-0000-0000-000000000002");

    @Autowired
    private MarketTransactionService transactions;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aceptaTiposP2PYConsultaElHistorialDelUsuarioAutenticado() {
        assertThatCode(() -> transactions.consultar(USER_ID,
                new MarketTransactionFilter(null, "P2P_PURCHASE", null, null, 1, 20)))
                .doesNotThrowAnyException();
    }

    @Test
    void unificaCompraVentaLegacyYAsientosP2PConPaginacionYAislamiento() {
        MarketP2PFixtureBuilder builder = new MarketP2PFixtureBuilder(
                jdbc, Clock.fixed(FIXTURE_TIME, ZoneOffset.UTC));
        var seller = builder.createUser("history_seller", new BigDecimal("0.00"));
        var buyer = builder.createUser("history_buyer", new BigDecimal("10.00"));
        var thirdSeller = builder.createUser("history_third", new BigDecimal("0.00"));
        var player = builder.createPlayer("History Player");
        builder.createHolding(seller, player, 4L, new BigDecimal("4.00000000"));
        builder.createHolding(buyer, player, 0L, BigDecimal.ZERO.setScale(8));
        builder.createHolding(thirdSeller, player, 2L, new BigDecimal("2.00000000"));
        var quote = builder.createQuote(player, new BigDecimal("2.00"), 1L);
        var counterparties = jdbc.query("SELECT id FROM market_counterparties ORDER BY id LIMIT 1",
                (rs, row) -> rs.getObject(1, UUID.class));
        UUID counterparty = counterparties.isEmpty() ? UUID.randomUUID() : counterparties.get(0);
        if (counterparties.isEmpty()) {
            jdbc.update("INSERT INTO market_counterparties "
                    + "(id, counterparty_type, display_name, created_at) "
                    + "VALUES (?, 'SUPERUSER', 'History Superuser', ?)", counterparty,
                    OffsetDateTime.ofInstant(FIXTURE_TIME, ZoneOffset.UTC));
        }

        var offerRequest = builder.createIdempotency(seller,
                "history-offer-" + UUID.randomUUID(), "P2P_OFFER", "history-offer-hash");
        var offer = builder.createOffer(seller, player, offerRequest, 2L);
        var saleRequest = builder.createIdempotency(buyer,
                "history-buy-" + UUID.randomUUID(), "P2P_PURCHASE", "history-buy-hash");
        insertTrade(TRADE_SALE, offer.id(), seller.id(), buyer.id(), player.id(),
                player.name(), quote, saleRequest.id(), ENTRY_SALE, ENTRY_PURCHASE,
                seller.id(), buyer.id(), FIXTURE_TIME.plusSeconds(30));

        var reverseOfferRequest = builder.createIdempotency(thirdSeller,
                "history-reverse-offer-" + UUID.randomUUID(), "P2P_OFFER", "reverse-offer-hash");
        var reverseOffer = builder.createOffer(thirdSeller, player, reverseOfferRequest, 1L);
        var purchaseRequest = builder.createIdempotency(seller,
                "history-reverse-buy-" + UUID.randomUUID(), "P2P_PURCHASE", "reverse-buy-hash");
        insertTrade(TRADE_PURCHASE, reverseOffer.id(), thirdSeller.id(), seller.id(), player.id(),
                player.name(), quote, purchaseRequest.id(),
                UUID.fromString("20000000-0000-0000-0000-000000000003"),
                UUID.fromString("20000000-0000-0000-0000-000000000004"),
                thirdSeller.id(), seller.id(), FIXTURE_TIME.plusSeconds(30));

        var primaryRequest = builder.createIdempotency(seller,
                "history-primary-" + UUID.randomUUID(), "PURCHASE", "primary-hash");
        var legacySaleRequest = builder.createIdempotency(seller,
                "history-legacy-sale-" + UUID.randomUUID(), "SALE", "legacy-sale-hash");
        OffsetDateTime primaryTime = OffsetDateTime.ofInstant(FIXTURE_TIME.plusSeconds(10),
                ZoneOffset.UTC);
        OffsetDateTime legacySaleTime = OffsetDateTime.ofInstant(FIXTURE_TIME.plusSeconds(20),
                ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO market_confirmed_transactions
                    (id, user_id, player_id, player_name_snapshot, operation_type,
                     quantity, unit_price, total_amount, counterparty_id, quote_id,
                     quote_version, idempotency_request_id, realized_pnl, confirmed_at)
                VALUES (?, ?, ?, ?, 'PURCHASE', 1, ?, ?, ?, ?, ?, ?, NULL, ?),
                       (?, ?, ?, ?, 'SALE', 1, ?, ?, ?, ?, ?, ?, ?, ?)
                """, LEGACY_PURCHASE, seller.id(), player.id(), player.name(),
                new BigDecimal("2.00"), new BigDecimal("2.00"), counterparty,
                quote.quoteId(), quote.version(), primaryRequest.id(),
                primaryTime, LEGACY_SALE, seller.id(), player.id(),
                player.name(), new BigDecimal("2.00"), new BigDecimal("2.00"), counterparty,
                quote.quoteId(), quote.version(), legacySaleRequest.id(),
                new BigDecimal("0.50000000"), legacySaleTime);

        var firstPage = transactions.consultar(seller.id(),
                new MarketTransactionFilter(null, null, null, null, 1, 2));
        var secondPage = transactions.consultar(seller.id(),
                new MarketTransactionFilter(null, null, null, null, 2, 2));
        var all = transactions.consultar(seller.id(),
                new MarketTransactionFilter(null, null, null, null, 1, 20));
        var buyerHistory = transactions.consultar(buyer.id(),
                new MarketTransactionFilter(null, null, null, null, 1, 20));

        assertThat(all.paginacion().totalRegistros()).isEqualTo(4L);
        assertThat(all.transacciones()).extracting(record -> record.tipoOperacion())
                .containsExactly("P2P_PURCHASE", "P2P_SALE", "SALE", "PRIMARY_PURCHASE");
        assertThat(firstPage.transacciones()).extracting(record -> record.tipoOperacion())
                .containsExactly("P2P_PURCHASE", "P2P_SALE");
        assertThat(secondPage.transacciones()).extracting(record -> record.tipoOperacion())
                .containsExactly("SALE", "PRIMARY_PURCHASE");
        assertThat(firstPage.transacciones()).doesNotHaveDuplicates();
        assertThat(secondPage.transacciones()).doesNotHaveDuplicates();
        assertThat(buyerHistory.transacciones()).extracting(record -> record.tipoOperacion())
                .containsExactly("P2P_PURCHASE");
        assertThat(all.transacciones()).allMatch(record -> record.usuarioId().equals(seller.id()));
        assertThat(offer.id()).isNotNull();
    }

    private void insertTrade(UUID tradeId, UUID offerId, UUID sellerId, UUID buyerId,
            UUID playerId, String playerName, MarketP2PFixtureBuilder.FixtureQuote quote,
            UUID requestId, UUID saleEntryId, UUID purchaseEntryId, UUID saleUserId,
            UUID purchaseUserId, Instant confirmedAt) {
        OffsetDateTime timestamp = OffsetDateTime.ofInstant(confirmedAt, ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO market_p2p_trades
                    (id, offer_id, seller_user_id, buyer_user_id, player_id,
                     player_name_snapshot, quantity, unit_price, total_amount,
                     quote_id, quote_version, idempotency_request_id, realized_pnl,
                     confirmed_at)
                VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?)
                """, tradeId, offerId, sellerId, buyerId, playerId, playerName,
                quote.price(), quote.price(), quote.quoteId(), quote.version(), requestId,
                new BigDecimal("1.00000000"), timestamp);
        jdbc.update("""
                INSERT INTO market_p2p_trade_entries
                    (id, trade_id, user_id, entry_role, quantity, amount, created_at)
                VALUES (?, ?, ?, 'P2P_SALE', 1, ?, ?),
                       (?, ?, ?, 'P2P_PURCHASE', 1, ?, ?)
                """, saleEntryId, tradeId, saleUserId, quote.price(), timestamp,
                purchaseEntryId, tradeId, purchaseUserId, quote.price(), timestamp);
    }
}
