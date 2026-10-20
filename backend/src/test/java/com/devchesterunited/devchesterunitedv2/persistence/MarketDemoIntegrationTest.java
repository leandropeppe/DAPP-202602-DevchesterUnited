package com.devchesterunited.devchesterunitedv2.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketPortfolioResult;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketPurchaseRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketSaleRequest;
import com.devchesterunited.devchesterunitedv2.service.dto.MarketTransactionFilter;
import com.devchesterunited.devchesterunitedv2.service.dto.QuotePublicationRequest;
import com.devchesterunited.devchesterunitedv2.service.interfaces.MarketPortfolioService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.MarketPurchaseService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.MarketSaleService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.MarketTransactionService;
import com.devchesterunited.devchesterunitedv2.service.interfaces.QuotePublicationPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** T073: demostracion reproducible aislada con cuatro usuarios y cinco jugadores. */
@SpringBootTest(properties = "token-market.initialization.auto-start=false")
class MarketDemoIntegrationTest extends PostgresTestSupport {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private QuotePublicationPort quotes;
    @Autowired private MarketPurchaseService purchases;
    @Autowired private MarketSaleService sales;
    @Autowired private MarketPortfolioService portfolios;
    @Autowired private MarketTransactionService transactions;

    @Test
    void escenarioDeDemostracionComparaPortfoliosVentasYResultadosPorVersion() {
        var fixture = new com.devchesterunited.devchesterunitedv2.support.MarketDemoFixtureBuilder(jdbc)
                .create();
        assertThat(fixture.users()).hasSize(4);
        assertThat(fixture.players()).hasSize(5);

        List<com.devchesterunited.devchesterunitedv2.service.dto.QuoteVersion> initialQuotes =
                new ArrayList<>();
        for (var player : fixture.players()) {
            initialQuotes.add(quotes.publishInitialQuote(player.id(), new BigDecimal("1.00"), true));
        }

        buy(purchases, fixture, 0, 0, 10, initialQuotes.get(0));
        buy(purchases, fixture, 1, 1, 20, initialQuotes.get(1));
        buy(purchases, fixture, 2, 2, 5, initialQuotes.get(2));
        buy(purchases, fixture, 3, 3, 8, initialQuotes.get(3));

        Instant revisedAt = Instant.now().minusSeconds(1);
        List<com.devchesterunited.devchesterunitedv2.service.dto.QuoteVersion> revisedQuotes =
                new ArrayList<>();
        for (int index = 0; index < fixture.players().size(); index++) {
            revisedQuotes.add(quotes.publish(new QuotePublicationRequest(
                    fixture.players().get(index).id(), new BigDecimal("1.50"), revisedAt,
                    revisedAt, null, "T073-controlled-demo")));
        }

        var firstSale = sales.vender(new MarketSaleRequest(
                fixture.users().get(0).id(), "t073-sale-u1", fixture.players().get(0).id(),
                4L, revisedQuotes.get(0).quoteId(), revisedQuotes.get(0).version()));
        var closedSale = sales.vender(new MarketSaleRequest(
                fixture.users().get(2).id(), "t073-sale-u3", fixture.players().get(2).id(),
                5L, revisedQuotes.get(2).quoteId(), revisedQuotes.get(2).version()));

        assertThat(firstSale.estado().name()).isEqualTo("CONFIRMED");
        assertThat(closedSale.estado().name()).isEqualTo("CONFIRMED");

        MarketPortfolioResult userOne = portfolios.consultar(fixture.users().get(0).id());
        MarketPortfolioResult userTwo = portfolios.consultar(fixture.users().get(1).id());
        MarketPortfolioResult userThree = portfolios.consultar(fixture.users().get(2).id());

        assertThat(userOne.posicionesAbiertas()).singleElement().satisfies(position -> {
            assertThat(position.jugadorId()).isEqualTo(fixture.players().get(0).id());
            assertThat(position.cantidad()).isEqualTo(6L);
            assertThat(position.costoTotalPendiente()).isEqualByComparingTo("6.00000000");
            assertThat(position.valorActual()).hasValueSatisfying(value ->
                    assertThat(value).isEqualByComparingTo("9.00000000"));
            assertThat(position.pnlNoRealizado()).hasValueSatisfying(value ->
                    assertThat(value).isEqualByComparingTo("3.00000000"));
            assertThat(position.cotizacion()).hasValueSatisfying(quote ->
                    assertThat(quote.version()).isEqualTo(2L));
        });
        assertThat(userOne.pnlRealizado()).isEqualByComparingTo("2.00000000");
        assertThat(userOne.resultadoCombinado()).hasValueSatisfying(value ->
                assertThat(value).isEqualByComparingTo("5.00000000"));

        assertThat(userTwo.posicionesAbiertas()).singleElement()
                .extracting(position -> position.cantidad()).isEqualTo(20L);
        assertThat(userTwo.pnlRealizado()).isEqualByComparingTo("0.00000000");
        assertThat(userTwo.pnlNoRealizado()).hasValueSatisfying(value ->
                assertThat(value).isEqualByComparingTo("10.00000000"));

        assertThat(userThree.posicionesAbiertas()).isEmpty();
        assertThat(userThree.pnlRealizado()).isEqualByComparingTo("2.50000000");

        var userOneHistory = transactions.consultar(fixture.users().get(0).id(),
                new MarketTransactionFilter(null, null, null, null, 1, 20));
        var userThreeHistory = transactions.consultar(fixture.users().get(2).id(),
                new MarketTransactionFilter(null, null, null, null, 1, 20));

        assertThat(userOneHistory.transacciones()).hasSize(2);
        assertThat(userOneHistory.transacciones()).extracting(record -> record.quoteVersion())
                .containsExactly(2L, 1L);
        assertThat(userOneHistory.transacciones().get(0).pnlRealizado())
                .isEqualByComparingTo("2.00000000");
        assertThat(userThreeHistory.transacciones()).hasSize(2);
        assertThat(userThreeHistory.transacciones().get(0).pnlRealizado())
                .isEqualByComparingTo("2.50000000");

        assertThat(userOne.posicionesAbiertas()).isNotEqualTo(userTwo.posicionesAbiertas());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_quote_versions "
                + "WHERE player_id IN (?, ?, ?, ?, ?)", Long.class,
                fixture.players().stream().map(player -> player.id()).toArray()))
                .isEqualTo(10L);
    }

    private static void buy(MarketPurchaseService purchases,
            com.devchesterunited.devchesterunitedv2.support.MarketDemoFixtureBuilder.DemoFixture fixture,
            int userIndex, int playerIndex, long quantity,
            com.devchesterunited.devchesterunitedv2.service.dto.QuoteVersion quote) {
        var result = purchases.comprar(new MarketPurchaseRequest(
                fixture.users().get(userIndex).id(), "t073-buy-u" + (userIndex + 1),
                fixture.players().get(playerIndex).id(), quantity,
                quote.quoteId(), quote.version()));
        assertThat(result.estado().name()).isEqualTo("CONFIRMED");
    }
}
