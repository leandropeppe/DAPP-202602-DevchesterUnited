package com.devchesterunited.devchesterunitedv2.integration.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/** T018: verifica V21 sobre PostgreSQL 16 descartable. */
@SpringBootTest(properties = "token-market.initialization.auto-start=false")
@Testcontainers
class MarketP2PSchemaIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("devchester_p2p_schema_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aplicaV21ConConstraintsFksIndicesYReservaEmbebida() {
        assertThat(jdbc.queryForObject("SELECT success FROM flyway_schema_history "
                + "WHERE version = '21'", Boolean.class)).isTrue();
        assertThat(columnExists("market_holdings", "reserved_quantity")).isTrue();
        assertThat(tableExists("market_p2p_offers")).isTrue();
        assertThat(tableExists("market_p2p_trades")).isTrue();
        assertThat(tableExists("market_p2p_trade_entries")).isTrue();
        assertThat(tableExists("market_p2p_offer_cancellations")).isTrue();
        assertThat(tableExists("market_p2p_reservations")).isFalse();
        assertThat(constraintExists("ck_market_holding_reserved_quantity")).isTrue();
        assertThat(constraintExists("fk_market_p2p_trade_buyer_holding")).isTrue();
        assertThat(indexExists("ix_market_p2p_offers_player_status_created")).isTrue();
        assertThat(indexExists("ix_market_p2p_entries_user_date")).isTrue();
    }

    @Test
    void reservaNuevaSeInicializaEnCero() {
        assertThat(jdbc.queryForObject("SELECT column_default FROM information_schema.columns "
                + "WHERE table_name = 'market_holdings' AND column_name = 'reserved_quantity'",
                String.class)).contains("0");
    }

    private boolean tableExists(String table) {
        return jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_name = ?)", Boolean.class, table);
    }

    private boolean columnExists(String table, String column) {
        return jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM information_schema.columns "
                + "WHERE table_schema = 'public' AND table_name = ? AND column_name = ?)",
                Boolean.class, table, column);
    }

    private boolean constraintExists(String constraint) {
        return jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM pg_constraint "
                + "WHERE conname = ?)", Boolean.class, constraint);
    }

    private boolean indexExists(String index) {
        return jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM pg_indexes "
                + "WHERE schemaname = 'public' AND indexname = ?)", Boolean.class, index);
    }
}
