package com.devchesterunited.devchesterunitedv2.integration.persistence;

import com.devchesterunited.devchesterunitedv2.persistence.market.projection.MarketP2POfferProjection;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.OfertaVentaP2PRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2POfferQuery;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2POfferQueryService;
import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.support.fixtures.market.MarketP2PFixtureBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "token-market.initialization.auto-start=false")
class MarketP2POfferQueryRepositoryIntegrationTest extends PostgresTestSupport {
    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private OfertaVentaP2PRepository offers;

    @Autowired
    private MarketP2POfferQueryService queryService;

    private MarketP2PFixtureBuilder.FixtureUser seller;
    private MarketP2PFixtureBuilder.FixtureUser buyer;
    private MarketP2PFixtureBuilder.FixturePlayer player;
    private MarketP2PFixtureBuilder.FixturePlayer noQuotePlayer;
    private MarketP2PFixtureBuilder.FixturePlayer expiredPlayer;
    private MarketP2PFixtureBuilder.FixturePlayer invalidatedPlayer;
    private List<MarketP2PFixtureBuilder.FixtureOffer> fixtureOffers;

    @BeforeEach
    void fixture() {
        MarketP2PFixtureBuilder builder = new MarketP2PFixtureBuilder(jdbc,
                Clock.fixed(NOW, ZoneOffset.UTC));
        seller = builder.createUser("query-seller-" + UUID.randomUUID(), BigDecimal.ZERO);
        buyer = builder.createUser("query-buyer-" + UUID.randomUUID(), BigDecimal.ZERO);
        player = builder.createPlayer("P2P Query Player " + UUID.randomUUID());
        builder.createHolding(seller, player, 50L, BigDecimal.TEN);
        builder.createQuote(player, new BigDecimal("2.50"), 1L);
        fixtureOffers = java.util.stream.IntStream.range(0, 5)
                .mapToObj(index -> {
                    var request = builder.createIdempotency(seller,
                            "query-offer-" + UUID.randomUUID(), "P2P_OFFER", "a".repeat(64));
                    return builder.createOffer(seller, player, request, index + 1L);
                }).toList();
        noQuotePlayer = builder.createPlayer("P2P No Quote Player " + UUID.randomUUID());
        expiredPlayer = builder.createPlayer("P2P Expired Quote Player " + UUID.randomUUID());
        invalidatedPlayer = builder.createPlayer("P2P Invalidated Quote Player " + UUID.randomUUID());
        builder.createHolding(seller, noQuotePlayer, 5L, BigDecimal.ONE);
        builder.createHolding(seller, expiredPlayer, 5L, BigDecimal.ONE);
        builder.createHolding(seller, invalidatedPlayer, 5L, BigDecimal.ONE);
        createOffer(builder, noQuotePlayer, "no-header");
        createExpiredQuote(expiredPlayer);
        createOffer(builder, expiredPlayer, "expired");
        builder.createQuote(invalidatedPlayer, new BigDecimal("2.50"), 1L);
        jdbc.update("UPDATE market_quote_headers SET validity_status = 'INVALIDATED', "
                + "invalidated_at = ?, invalidation_reason = 'test' WHERE player_id = ?",
                Timestamp.from(NOW), invalidatedPlayer.id());
        createOffer(builder, invalidatedPlayer, "invalidated");
    }

    @Test
    void filtraEstadoDerivadoAntesDeLimitarYDevuelveConteoExacto() {
        var page = offers.buscarPublico(player.id(), "PURCHASABLE", false, buyer.id(), NOW,
                PageRequest.of(1, 2));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.getContent()).extracting(MarketP2POfferProjection::getPlayerId)
                .containsOnly(player.id());
        assertThat(page.getContent()).extracting(MarketP2POfferProjection::getQuoteValidityStatus)
                .containsOnly("VALID");
    }

    @Test
    void ordenaPorFechaDescendenteYIdDescendenteDeFormaDeterminista() {
        var page = offers.buscarPublico(player.id(), null, false, buyer.id(), NOW,
                PageRequest.of(0, 10));
        List<UUID> ids = page.getContent().stream()
                .map(MarketP2POfferProjection::getOfferId).toList();

        assertThat(ids).containsExactlyElementsOf(ids.stream()
                .sorted(Comparator.comparing(UUID::toString).reversed()).toList())
                .containsExactlyElementsOf(fixtureOffers.stream()
                        .map(MarketP2PFixtureBuilder.FixtureOffer::id)
                        .sorted(Comparator.comparing(UUID::toString).reversed()).toList());
    }

    @Test
    void ofertaSinHeaderApareceComoSuspendidaYSeCuenta() {
        var result = queryService.consultar(new MarketP2POfferQuery(buyer.id(),
                noQuotePlayer.id(), "SUSPENDED", false, 1, 2));

        assertThat(result.offers()).hasSize(1);
        assertThat(result.offers().get(0).availability().status()).isEqualTo("SUSPENDED");
        assertThat(result.offers().get(0).quote()).isNull();
        assertThat(result.pagination().totalRegistros()).isEqualTo(1L);
        assertThat(result.pagination().totalPaginas()).isEqualTo(1L);
    }

    @Test
    void ofertasConQuoteVencidaOHeaderInvalidadoSonSuspendidas() {
        var expired = queryService.consultar(new MarketP2POfferQuery(buyer.id(),
                expiredPlayer.id(), "SUSPENDED", false, 1, 2));
        var invalidated = queryService.consultar(new MarketP2POfferQuery(buyer.id(),
                invalidatedPlayer.id(), "SUSPENDED", false, 1, 2));

        assertThat(expired.offers()).singleElement().extracting(view -> view.availability().status())
                .isEqualTo("SUSPENDED");
        assertThat(invalidated.offers()).singleElement()
                .extracting(view -> view.availability().status()).isEqualTo("SUSPENDED");
    }

    private void createOffer(MarketP2PFixtureBuilder builder,
            MarketP2PFixtureBuilder.FixturePlayer offerPlayer, String label) {
        var request = builder.createIdempotency(seller, "query-" + label + "-" + UUID.randomUUID(),
                "P2P_OFFER", "b".repeat(64));
        builder.createOffer(seller, offerPlayer, request, 2L);
    }

    private void createExpiredQuote(MarketP2PFixtureBuilder.FixturePlayer quotePlayer) {
        UUID quoteId = UUID.randomUUID();
        Timestamp publishedAt = Timestamp.from(NOW.minusSeconds(120));
        Timestamp validUntil = Timestamp.from(NOW.minusSeconds(1));
        jdbc.update("INSERT INTO market_quote_versions "
                        + "(quote_id, version, player_id, price_per_token, published_at, valid_from, "
                        + "valid_until, validity_status, strategy_reference, created_at) "
                        + "VALUES (?, 1, ?, 2.50, ?, ?, ?, 'VALID', 'P2P_TEST', ?)",
                quoteId, quotePlayer.id(), publishedAt, publishedAt, validUntil, publishedAt);
        jdbc.update("INSERT INTO market_quote_headers "
                        + "(player_id, quote_id, current_version, updated_at) VALUES (?, ?, 1, ?)",
                quotePlayer.id(), quoteId, Timestamp.from(NOW));
    }
}
