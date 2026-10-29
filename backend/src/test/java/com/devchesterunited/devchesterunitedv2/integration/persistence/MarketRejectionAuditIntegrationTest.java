package com.devchesterunited.devchesterunitedv2.integration.persistence;

import com.devchesterunited.devchesterunitedv2.persistence.auth.entity.UsuarioEntity;
import com.devchesterunited.devchesterunitedv2.persistence.auth.repository.UsuarioRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.AuditoriaRechazoMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.AuditoriaRechazoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.MovimientoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.TransaccionConfirmadaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketRejectedAttempt;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketRejectionAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pruebas de persistencia y frontera transaccional de la auditoría interna de T062. */
@SpringBootTest(properties = "token-market.initialization.auto-start=false")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MarketRejectionAuditIntegrationTest {
    private static final String PASSWORD_HASH =
            "$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW";
    private static final Instant FIRST_REJECTION = Instant.parse("2026-10-19T12:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("devchester_rejection_audit_test")
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
    private AuditoriaRechazoMercadoRepository auditorias;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private JugadorRepository jugadores;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransaccionConfirmadaMercadoRepository transacciones;

    @Autowired
    private MovimientoMercadoRepository movimientos;

    @Autowired
    private MarketRejectionAuditService auditService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void persisteRechazosDeCompraYVentaConLosCamposPermitidos() {
        UUID usuarioId = crearUsuario();
        UUID jugadorId = crearJugador();
        String purchaseKeyHash = sha256Like("purchase-key");
        String purchaseParametersHash = sha256Like("purchase-parameters");
        String saleKeyHash = sha256Like("sale-key");
        String saleParametersHash = sha256Like("sale-parameters");

        auditService.registrar(new MarketRejectedAttempt(usuarioId, "PURCHASE", jugadorId,
                purchaseKeyHash, "INSUFFICIENT_BALANCE", purchaseParametersHash,
                FIRST_REJECTION));
        auditService.registrar(new MarketRejectedAttempt(usuarioId, "SALE", jugadorId,
                saleKeyHash, "INSUFFICIENT_INVENTORY", saleParametersHash,
                FIRST_REJECTION.plusSeconds(1)));

        assertThat(auditorias.findAllByUsuarioIdOrderByCreadoEnDesc(usuarioId))
                .extracting(AuditoriaRechazoMercadoEntity::getTipoOperacion)
                .containsExactly("SALE", "PURCHASE");
        assertThat(auditorias.findAllByUsuarioIdOrderByCreadoEnDesc(usuarioId))
                .extracting(AuditoriaRechazoMercadoEntity::getHashClaveIdempotencia)
                .containsExactly(saleKeyHash, purchaseKeyHash);
    }

    @Test
    void persisteRechazoSinUsuarioNiJugador() {
        Instant createdAt = FIRST_REJECTION.plusSeconds(10);

        auditService.registrar(new MarketRejectedAttempt(null, "SALE", null,
                null, "INVALID_INPUT", null, createdAt));

        assertThat(auditorias.findAll())
                .filteredOn(auditoria -> createdAt.equals(auditoria.getCreadoEn()))
                .singleElement()
                .satisfies(auditoria -> {
                    assertThat(auditoria.getUsuarioId()).isNull();
                    assertThat(auditoria.getJugadorId()).isNull();
                    assertThat(auditoria.getTipoOperacion()).isEqualTo("SALE");
                    assertThat(auditoria.getCodigoMotivo()).isEqualTo("INVALID_INPUT");
                });
    }

    @Test
    void laAuditoriaSobreviveAlRollbackDeLaTransaccionExterior() {
        UUID usuarioId = crearUsuario();
        Instant createdAt = FIRST_REJECTION.plusSeconds(20);
        MarketRejectedAttempt attempt = new MarketRejectedAttempt(usuarioId, "PURCHASE", null,
                sha256Like("rollback-key"), "INSUFFICIENT_BALANCE",
                sha256Like("rollback-parameters"), createdAt);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                    auditService.registrar(attempt);
                    status.setRollbackOnly();
                    throw new IllegalStateException("rollback de operación principal");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("rollback de operación principal");

        assertThat(auditorias.findAllByUsuarioIdOrderByCreadoEnDesc(usuarioId))
                .anySatisfy(auditoria -> {
                    assertThat(auditoria.getCodigoMotivo()).isEqualTo("INSUFFICIENT_BALANCE");
                    assertThat(auditoria.getCreadoEn()).isEqualTo(createdAt);
                });
    }

    @Test
    void registrarAuditoriaNoCreaTransaccionesConfirmadasNiMovimientos() {
        UUID usuarioId = crearUsuario();

        auditService.registrar(new MarketRejectedAttempt(usuarioId, "PURCHASE", null,
                sha256Like("no-mutation-key"), "INSUFFICIENT_BALANCE",
                sha256Like("no-mutation-parameters"), FIRST_REJECTION.plusSeconds(30)));

        assertThat(transacciones.findAllByUsuarioIdOrderByConfirmadoEnDesc(usuarioId)).isEmpty();
        assertThat(movimientos.findAll()).isEmpty();
    }

    private UUID crearUsuario() {
        UUID id = UUID.randomUUID();
        String suffix = id.toString().replace("-", "");
        usuarios.save(new UsuarioEntity(id, "audit" + suffix, suffix + "@audit.test",
                PASSWORD_HASH, FIRST_REJECTION, FIRST_REJECTION));
        return id;
    }

    private UUID crearJugador() {
        UUID id = UUID.randomUUID();
        UUID equipoId = jdbc.queryForObject("SELECT id FROM equipos ORDER BY id LIMIT 1", UUID.class);
        jugadores.save(new JugadorEntity(id, "Audit Player " + id, equipoId, "Forward", 9,
                "Argentina", true));
        return id;
    }

    private static String sha256Like(String value) {
        return switch (value) {
            case "purchase-key" -> "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
            case "purchase-parameters" -> "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
            case "sale-key" -> "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";
            case "sale-parameters" -> "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd";
            case "rollback-key" -> "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";
            case "rollback-parameters" -> "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";
            case "no-mutation-key" -> "1111111111111111111111111111111111111111111111111111111111111111";
            case "no-mutation-parameters" -> "2222222222222222222222222222222222222222222222222222222222222222";
            default -> throw new IllegalArgumentException("fixture hash no definido");
        };
    }
}
