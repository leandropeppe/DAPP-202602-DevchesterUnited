package com.devchesterunited.devchesterunitedv2.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketInitializationCoordinator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "token-market.initialization.auto-start=true")
class MarketInitializationApplicationReadyIntegrationTest extends PostgresTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired MarketInitializationCoordinator coordinador;

    @AfterEach
    void limpiarDatosDelArranqueReal() {
        jdbc.execute("TRUNCATE TABLE market_movements, market_confirmed_transactions, "
                + "market_rejected_attempts, market_idempotency_requests, market_holdings, "
                + "market_realized_pnl, market_quote_headers, market_quote_versions, "
                + "market_initial_credit_grants, market_token_issuances, "
                + "market_credit_accounts, market_counterparties, market_state CASCADE");
    }

    @Test
    void applicationReadyEjecutaLaInicializacionYUnReintentoEsIdempotente() {
        assertThat(estado()).isEqualTo("READY");
        assertThat(contarSuperusuarios()).isEqualTo(1L);

        coordinador.coordinar();

        assertThat(estado()).isEqualTo("READY");
        assertThat(contarSuperusuarios()).isEqualTo(1L);
    }

    private String estado() {
        return jdbc.queryForObject("SELECT status FROM market_state", String.class);
    }

    private long contarSuperusuarios() {
        return jdbc.queryForObject("SELECT count(*) FROM market_counterparties "
                + "WHERE counterparty_type = 'SUPERUSER'", Long.class);
    }
}
