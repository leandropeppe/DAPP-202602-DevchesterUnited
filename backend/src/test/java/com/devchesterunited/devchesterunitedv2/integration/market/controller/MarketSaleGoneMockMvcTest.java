package com.devchesterunited.devchesterunitedv2.integration.market.controller;

import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketSaleService;
import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "token-market.initialization.auto-start=false")
@AutoConfigureMockMvc
class MarketSaleGoneMockMvcTest extends PostgresTestSupport {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private MarketSaleService sales;

    @Test
    void ventaRetiradaDevuelve410SinLiquidarNiPersistir() throws Exception {
        UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        Snapshot before = snapshot();

        mvc.perform(post("/api/v1/market/sales")
                        .with(jwt().jwt(token -> token.subject("usr_" + userId)))
                        .header("Idempotency-Key", "retired-sale")
                        .contentType("application/json")
                        .content("{\"player_id\":\"11111111-1111-1111-1111-111111111111\","
                                + "\"quantity\":1,\"quote_id\":\"22222222-2222-2222-2222-222222222222\","
                                + "\"quote_version\":1}"))
                .andExpect(status().isGone());

        assertThat(snapshot()).isEqualTo(before);
        verifyNoInteractions(sales);
    }

    private Snapshot snapshot() {
        return new Snapshot(countAccounts(), countHoldings(), countMovements(),
                countTransactions(), countIdempotencyRequests());
    }

    private long countAccounts() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM market_credit_accounts", Long.class);
    }

    private long countHoldings() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM market_holdings", Long.class);
    }

    private long countMovements() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM market_movements", Long.class);
    }

    private long countTransactions() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM market_confirmed_transactions", Long.class);
    }

    private long countIdempotencyRequests() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM market_idempotency_requests", Long.class);
    }

    private record Snapshot(long accounts, long holdings, long movements,
            long transactions, long idempotencyRequests) { }
}
