package com.devchesterunited.devchesterunitedv2.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devchesterunited.devchesterunitedv2.modelo.EstadoMercado;
import com.devchesterunited.devchesterunitedv2.service.impl.MarketInitializationStateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class MarketInitializationStateServiceIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("devchester_state_test")
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

    @Autowired
    private MarketInitializationStateService estados;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    @AfterEach
    void limpiarSoloElEstadoDeEstaSuite() {
        jdbc.update("DELETE FROM market_state");
    }

    @Test
    void persisteLaSecuenciaDeTransicionesEnTransaccionesIndependientes() {
        var inicial = estados.comenzarOReanudar();
        assertThat(inicial.estado()).isEqualTo(EstadoMercado.INITIALIZING);
        assertThat(inicial.epoca()).isEqualTo(2L);
        assertThat(status()).isEqualTo("INITIALIZING");
        assertThat(epoch()).isEqualTo(2L);

        var fallido = estados.marcarFailed(2L, "fallo controlado").orElseThrow();
        assertThat(fallido.estado()).isEqualTo(EstadoMercado.FAILED);
        assertThat(fallido.epoca()).isEqualTo(3L);
        assertThat(status()).isEqualTo("FAILED");
        assertThat(epoch()).isEqualTo(3L);
        assertThat(reason()).isEqualTo("fallo controlado");

        var reintento = estados.comenzarOReanudar();
        assertThat(reintento.estado()).isEqualTo(EstadoMercado.INITIALIZING);
        assertThat(reintento.epoca()).isEqualTo(4L);
    }

    @Test
    void failedSobreviveAlRollbackDeLaTransaccionExterior() {
        estados.comenzarOReanudar();

        TransactionTemplate exterior = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> exterior.executeWithoutResult(status -> {
            estados.marcarFailed(2L, "fallo antes del rollback exterior");
            status.setRollbackOnly();
            throw new IllegalStateException("rollback exterior");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("rollback exterior");

        assertThat(status()).isEqualTo("FAILED");
        assertThat(epoch()).isEqualTo(3L);
        assertThat(reason()).isEqualTo("fallo antes del rollback exterior");
    }

    @Test
    void unaEjecucionAntiguaNoPuedeSobrescribirUnEpochPosterior() {
        estados.comenzarOReanudar();

        assertThat(estados.marcarFailed(1L, "epoch antiguo")).isEmpty();
        assertThat(status()).isEqualTo("INITIALIZING");
        assertThat(epoch()).isEqualTo(2L);
        assertThat(reason()).isNull();
    }

    private Long epoch() {
        return jdbc.queryForObject("SELECT epoch FROM market_state", Long.class);
    }

    private String status() {
        return jdbc.queryForObject("SELECT status FROM market_state", String.class);
    }

    private String reason() {
        return jdbc.queryForObject("SELECT failure_reason FROM market_state", String.class);
    }
}
