package com.devchesterunited.devchesterunitedv2.integration.persistence;

import com.devchesterunited.devchesterunitedv2.exception.TokenMarketException;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketIdempotencyRequest;
import com.devchesterunited.devchesterunitedv2.service.impl.market.MarketIdempotencyClaimService;
import com.devchesterunited.devchesterunitedv2.support.fixtures.market.MarketP2PFixtureBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T013/T014: claim P2P, replay y conflicto sobre PostgreSQL 16 aislado. */
@SpringBootTest(properties = "token-market.initialization.auto-start=false")
@Testcontainers
class MarketP2PIdempotencyIntegrationTest {
    private static final UUID PLAYER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID QUOTE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("devchester_p2p_idempotency_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MarketIdempotencyClaimService claims;

    private UUID userId;

    @BeforeEach
    void createUser() {
        userId = new MarketP2PFixtureBuilder(jdbc)
                .createUser("idempotency", java.math.BigDecimal.valueOf(1000)).id();
    }

    @Test
    void aceptaOperacionP2PYReproduceElResultadoSinDuplicarFila() {
        var first = claims.reclamarConPropiedad(request("p2p-key", "P2P_OFFER", 4L));
        var replay = claims.reclamarConPropiedad(request("p2p-key", "P2P_OFFER", 4L));

        assertThat(first.propietarioEjecucion()).isTrue();
        assertThat(replay.propietarioEjecucion()).isFalse();
        assertThat(replay.resultado().solicitudId())
                .isEqualTo(first.resultado().solicitudId());
        assertThat(replay.resultado().usuarioId()).isEqualTo(first.resultado().usuarioId());
        assertThat(replay.resultado().claveIdempotencia())
                .isEqualTo(first.resultado().claveIdempotencia());
        assertThat(replay.resultado().tipoOperacion()).isEqualTo("P2P_OFFER");
        assertThat(replay.resultado().hashParametros())
                .isEqualTo(first.resultado().hashParametros());
        assertThat(replay.resultado().estado()).isEqualTo(first.resultado().estado());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM market_idempotency_requests "
                + "WHERE user_id = ? AND idempotency_key = 'p2p-key'", Long.class, userId))
                .isEqualTo(1L);
    }

    @Test
    void rechazaLaMismaClaveConParametrosP2PDiferentes() {
        claims.reclamarConPropiedad(request("conflict-key", "P2P_PURCHASE", 2L));

        assertThatThrownBy(() -> claims.reclamarConPropiedad(
                request("conflict-key", "P2P_PURCHASE", 3L)))
                .isInstanceOfSatisfying(TokenMarketException.class,
                        error -> assertThat(error.reason())
                                .isEqualTo(TokenMarketException.Reason.IDEMPOTENCY_CONFLICT));
    }

    private MarketIdempotencyRequest request(String key, String operation, long quantity) {
        return new MarketIdempotencyRequest(userId, key, operation, PLAYER_ID,
                quantity, QUOTE_ID, 1L);
    }
}
