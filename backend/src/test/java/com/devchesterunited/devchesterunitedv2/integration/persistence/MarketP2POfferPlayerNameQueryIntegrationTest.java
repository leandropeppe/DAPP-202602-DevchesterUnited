package com.devchesterunited.devchesterunitedv2.integration.persistence;

import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketP2POfferQuery;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketP2POfferQueryService;
import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.support.fixtures.market.MarketP2PFixtureBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "token-market.initialization.auto-start=false")
class MarketP2POfferPlayerNameQueryIntegrationTest extends PostgresTestSupport {
    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    @Autowired
    private MarketP2POfferQueryService queryService;

    @Autowired
    private JdbcTemplate jdbc;

    private MarketP2PFixtureBuilder.FixtureUser seller;
    private MarketP2PFixtureBuilder.FixtureUser buyer;
    private MarketP2PFixtureBuilder.FixturePlayer valverde;
    private String uniqueSearchTerm;

    @BeforeEach
    void fixture() {
        MarketP2PFixtureBuilder builder = new MarketP2PFixtureBuilder(jdbc,
                Clock.fixed(NOW, ZoneOffset.UTC));
        seller = builder.createUser("name-seller-" + UUID.randomUUID(), BigDecimal.ZERO);
        buyer = builder.createUser("name-buyer-" + UUID.randomUUID(), BigDecimal.ZERO);
        uniqueSearchTerm = UUID.randomUUID().toString().substring(0, 8);
        valverde = builder.createPlayer("Federico Valverde " + uniqueSearchTerm);
        var other = builder.createPlayer("Otro Jugador");
        builder.createHolding(seller, valverde, 100L, BigDecimal.TEN);
        builder.createHolding(seller, other, 100L, BigDecimal.TEN);
        builder.createQuote(valverde, new BigDecimal("2.50"), 1L);
        builder.createQuote(other, new BigDecimal("1.50"), 1L);
        for (int index = 0; index < 5; index++) {
            createOffer(builder, valverde, "valverde-" + index);
        }
        createOffer(builder, other, "other");
    }

    @Test
    void buscaPorNombreExactoParcialYCaseInsensitive() {
        var exact = queryService.consultar(query("Federico Valverde " + uniqueSearchTerm,
                null, false, 1, 20));
        var partial = queryService.consultar(query(uniqueSearchTerm.substring(0, 4),
                null, false, 1, 20));
        var upperCase = queryService.consultar(query(("federico valverde " + uniqueSearchTerm).toUpperCase(),
                null, false, 1, 20));

        assertThat(exact.offers()).hasSize(5);
        assertThat(partial.offers()).hasSize(5);
        assertThat(upperCase.offers()).hasSize(5);
        assertThat(exact.offers()).allSatisfy(offer ->
                assertThat(offer.identity().playerName()).isEqualTo("Federico Valverde " + uniqueSearchTerm));
    }

    @Test
    void aplicaResultadoVacioPaginacionStatusYMineAntesDeLimitar() {
        var empty = queryService.consultar(query("inexistente", null, false, 1, 20));
        var firstPage = queryService.consultar(query(uniqueSearchTerm, "PURCHASABLE", false, 1, 2));
        var secondPage = queryService.consultar(new MarketP2POfferQuery(seller.id(), null,
                uniqueSearchTerm, "PURCHASABLE", true, 2, 2));

        assertThat(empty.offers()).isEmpty();
        assertThat(firstPage.offers()).hasSize(2);
        assertThat(firstPage.pagination().totalRegistros()).isEqualTo(5L);
        assertThat(firstPage.pagination().totalPaginas()).isEqualTo(3L);
        assertThat(secondPage.offers()).hasSize(2);
        assertThat(secondPage.offers()).allSatisfy(offer ->
                assertThat(offer.identity().mine()).isTrue());
    }

    private MarketP2POfferQuery query(String name, String status, boolean mine, int page, int limit) {
        return new MarketP2POfferQuery(buyer.id(), null, name, status, mine, page, limit);
    }

    private void createOffer(MarketP2PFixtureBuilder builder,
            MarketP2PFixtureBuilder.FixturePlayer player, String label) {
        var request = builder.createIdempotency(seller, "name-query-" + label + "-" + UUID.randomUUID(),
                "P2P_OFFER", "c".repeat(64));
        builder.createOffer(seller, player, request, 2L);
    }
}
