package com.devchesterunited.devchesterunitedv2.integration.persistence;

import com.devchesterunited.devchesterunitedv2.persistence.market.entity.AuditoriaRechazoMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.ContraparteMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.SolicitudIdempotenciaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.TransaccionConfirmadaMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.VersionCotizacionMercadoEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.entity.VersionCotizacionMercadoId;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.AuditoriaRechazoMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.ContraparteMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.SolicitudIdempotenciaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.TransaccionConfirmadaMercadoRepository;
import com.devchesterunited.devchesterunitedv2.persistence.market.repository.VersionCotizacionMercadoRepository;

import com.devchesterunited.devchesterunitedv2.persistence.auth.entity.UsuarioEntity;
import com.devchesterunited.devchesterunitedv2.persistence.auth.repository.UsuarioRepository;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.repository.JugadorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TransaccionConfirmadaMercadoRepositoryIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("devchester_market_transactions_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    private static final Instant FIRST_CONFIRMATION = Instant.parse("2026-10-16T12:00:00Z");
    private static final String HASH =
            "$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW";

    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private JugadorRepository jugadores;
    @Autowired
    private ContraparteMercadoRepository contrapartes;
    @Autowired
    private VersionCotizacionMercadoRepository cotizaciones;
    @Autowired
    private SolicitudIdempotenciaMercadoRepository solicitudes;
    @Autowired
    private TransaccionConfirmadaMercadoRepository transacciones;
    @Autowired
    private AuditoriaRechazoMercadoRepository rechazos;

    @Test
    void consultaSoloElUsuarioSolicitanteYOrdenaPorConfirmacionDescendente() {
        Fixture fixture = fixture();
        TransaccionConfirmadaMercadoEntity older = transaction(fixture, fixture.playerOne(),
                "Older snapshot", "PURCHASE", 1L, "1.00", "1.00", null,
                FIRST_CONFIRMATION);
        TransaccionConfirmadaMercadoEntity newer = transaction(fixture, fixture.playerTwo(),
                "Newer snapshot", "SALE", 2L, "2.00", "4.00", "1.25000000",
                FIRST_CONFIRMATION.plusSeconds(20));
        TransaccionConfirmadaMercadoEntity anotherUser = transaction(fixture, fixture.playerOne(),
                "Other user snapshot", "PURCHASE", 3L, "1.00", "3.00", null,
                FIRST_CONFIRMATION.plusSeconds(40), fixture.otherUserId());

        List<TransaccionConfirmadaMercadoEntity> result =
                transacciones.findAllByUsuarioIdOrderByConfirmadoEnDesc(fixture.userId());

        assertThat(result).extracting(TransaccionConfirmadaMercadoEntity::getId)
                .containsExactly(newer.getId(), older.getId());
        assertThat(result).allMatch(transaction -> fixture.userId().equals(transaction.getUsuarioId()));
        assertThat(result).extracting(TransaccionConfirmadaMercadoEntity::getId)
                .doesNotContain(anotherUser.getId());
    }

    @Test
    void filtraPorJugadorSinIncluirOtraPosicionDelMismoUsuario() {
        Fixture fixture = fixture();
        TransaccionConfirmadaMercadoEntity requested = transaction(fixture, fixture.playerOne(),
                "Requested player", "PURCHASE", 1L, "1.00", "1.00", null,
                FIRST_CONFIRMATION);
        transaction(fixture, fixture.playerTwo(), "Other player", "SALE", 1L,
                "2.00", "2.00", "0.50000000", FIRST_CONFIRMATION.plusSeconds(10));

        List<TransaccionConfirmadaMercadoEntity> result =
                transacciones.findAllByUsuarioIdAndJugadorIdOrderByConfirmadoEnDesc(
                        fixture.userId(), fixture.playerOne());

        assertThat(result).extracting(TransaccionConfirmadaMercadoEntity::getId)
                .containsExactly(requested.getId());
        assertThat(result).allMatch(transaction -> fixture.playerOne().equals(transaction.getJugadorId()));
    }

    @Test
    void conservaElSnapshotYTodosLosDatosDeLaTransaccionConfirmada() {
        Fixture fixture = fixture();
        String snapshot = "Nombre historico antes del cambio";
        BigDecimal pnl = new BigDecimal("1.25000000");
        TransaccionConfirmadaMercadoEntity saved = transaction(fixture, fixture.playerOne(), snapshot,
                "SALE", 4L, "2.50", "10.00", pnl.toString(), FIRST_CONFIRMATION);

        TransaccionConfirmadaMercadoEntity loaded = transacciones.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getUsuarioId()).isEqualTo(fixture.userId());
        assertThat(loaded.getJugadorId()).isEqualTo(fixture.playerOne());
        assertThat(loaded.getNombreJugadorSnapshot()).isEqualTo(snapshot);
        assertThat(loaded.getTipoOperacion()).isEqualTo("SALE");
        assertThat(loaded.getQuantity()).isEqualTo(4L);
        assertThat(loaded.getPrecioUnitario()).isEqualByComparingTo("2.50");
        assertThat(loaded.getMontoTotal()).isEqualByComparingTo("10.00");
        assertThat(loaded.getContraparteId()).isEqualTo(fixture.counterpartyId());
        assertThat(loaded.getQuoteId()).isEqualTo(saved.getQuoteId());
        assertThat(loaded.getVersionCotizacion()).isEqualTo(1L);
        assertThat(loaded.getPnlRealizado()).isEqualByComparingTo(pnl);
        assertThat(loaded.getConfirmadoEn()).isEqualTo(FIRST_CONFIRMATION);
    }

    @Test
    void encuentraLaTransaccionPorLaSolicitudDeIdempotencia() {
        Fixture fixture = fixture();
        TransaccionConfirmadaMercadoEntity saved = transaction(fixture, fixture.playerOne(),
                "Idempotent snapshot", "PURCHASE", 2L, "1.00", "2.00", null,
                FIRST_CONFIRMATION);

        assertThat(transacciones.findBySolicitudIdempotenciaId(saved.getSolicitudIdempotenciaId()))
                .get()
                .extracting(TransaccionConfirmadaMercadoEntity::getId)
                .isEqualTo(saved.getId());
        assertThat(transacciones.findByIdAndUsuarioId(saved.getId(), fixture.userId()))
                .get()
                .extracting(TransaccionConfirmadaMercadoEntity::getId)
                .isEqualTo(saved.getId());
        assertThat(transacciones.findByIdAndUsuarioId(saved.getId(), fixture.otherUserId()))
                .isEmpty();
    }

    @Test
    void mantieneSeparadasLasTransaccionesConfirmadasDeLaAuditoriaDeRechazos() {
        Fixture fixture = fixture();
        TransaccionConfirmadaMercadoEntity confirmed = transaction(fixture, fixture.playerOne(),
                "Confirmed snapshot", "PURCHASE", 1L, "1.00", "1.00", null,
                FIRST_CONFIRMATION);
        AuditoriaRechazoMercadoEntity rejected = new AuditoriaRechazoMercadoEntity(
                UUID.randomUUID(), fixture.userId(), "SALE", fixture.playerOne(),
                "key-hash", "INSUFFICIENT_HOLDING", "parameters-hash",
                FIRST_CONFIRMATION.plusSeconds(1));
        rechazos.saveAndFlush(rejected);

        assertThat(transacciones.findAllByUsuarioIdOrderByConfirmadoEnDesc(fixture.userId()))
                .extracting(TransaccionConfirmadaMercadoEntity::getId)
                .containsExactly(confirmed.getId());
        assertThat(rechazos.findAllByUsuarioIdOrderByCreadoEnDesc(fixture.userId()))
                .extracting(AuditoriaRechazoMercadoEntity::getId)
                .containsExactly(rejected.getId());
        assertThat(transacciones.findById(rejected.getId())).isEmpty();
    }

    @Test
    void rechazaUpdateYDeleteDeTransaccionesConfirmadasMedianteTriggers() {
        Fixture fixture = fixture();
        TransaccionConfirmadaMercadoEntity saved = transaction(fixture, fixture.playerOne(),
                "Immutable snapshot", "PURCHASE", 1L, "1.00", "1.00", null,
                FIRST_CONFIRMATION);

        assertThatThrownBy(() -> inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "UPDATE market_confirmed_transactions SET player_name_snapshot = ? WHERE id = ?")) {
                statement.setString(1, "Tampered snapshot");
                statement.setObject(2, saved.getId());
                statement.executeUpdate();
            }
            return null;
        })).isInstanceOf(SQLException.class);

        assertThatThrownBy(() -> inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "DELETE FROM market_confirmed_transactions WHERE id = ?")) {
                statement.setObject(1, saved.getId());
                statement.executeUpdate();
            }
            return null;
        })).isInstanceOf(SQLException.class);

        assertThat(transacciones.findById(saved.getId())).isPresent()
                .get().extracting(TransaccionConfirmadaMercadoEntity::getNombreJugadorSnapshot)
                .isEqualTo("Immutable snapshot");
    }

    @Test
    void filtraPorTipoPurchaseYSale() {
        Fixture fixture = fixture();
        TransaccionConfirmadaMercadoEntity purchase = transaction(fixture, fixture.playerOne(),
                "Purchase", "PURCHASE", 1L, "1.00", "1.00", null, FIRST_CONFIRMATION);
        TransaccionConfirmadaMercadoEntity sale = transaction(fixture, fixture.playerTwo(),
                "Sale", "SALE", 1L, "2.00", "2.00", "0.50000000",
                FIRST_CONFIRMATION.plusSeconds(1));

        assertThat(buscar(fixture.userId(), null, "PURCHASE", null, null, 0, 10).getContent())
                .extracting(TransaccionConfirmadaMercadoEntity::getId)
                .containsExactly(purchase.getId());
        assertThat(buscar(fixture.userId(), null, "SALE", null, null, 0, 10).getContent())
                .extracting(TransaccionConfirmadaMercadoEntity::getId)
                .containsExactly(sale.getId());
    }

    @Test
    void aplicaDesdeInclusivoYHastaExclusivo() {
        Fixture fixture = fixture();
        TransaccionConfirmadaMercadoEntity lower = transaction(fixture, fixture.playerOne(),
                "Lower bound", "PURCHASE", 1L, "1.00", "1.00", null, FIRST_CONFIRMATION);
        transaction(fixture, fixture.playerTwo(),
                "Upper bound", "PURCHASE", 1L, "1.00", "1.00", null,
                FIRST_CONFIRMATION.plusSeconds(10));

        Page<TransaccionConfirmadaMercadoEntity> page = buscar(fixture.userId(), null, null,
                FIRST_CONFIRMATION, FIRST_CONFIRMATION.plusSeconds(10), 0, 10);

        assertThat(page.getContent()).extracting(TransaccionConfirmadaMercadoEntity::getId)
                .containsExactly(lower.getId());
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void combinaJugadorTipoYFechas() {
        Fixture fixture = fixture();
        TransaccionConfirmadaMercadoEntity expected = transaction(fixture, fixture.playerOne(),
                "Combined filter", "SALE", 1L, "2.00", "2.00", "0.50000000",
                FIRST_CONFIRMATION.plusSeconds(10));
        transaction(fixture, fixture.playerOne(), "Wrong type", "PURCHASE", 1L,
                "1.00", "1.00", null, FIRST_CONFIRMATION.plusSeconds(10));
        transaction(fixture, fixture.playerTwo(), "Wrong player", "SALE", 1L,
                "2.00", "2.00", "0.50000000", FIRST_CONFIRMATION.plusSeconds(10));

        Page<TransaccionConfirmadaMercadoEntity> page = buscar(fixture.userId(), fixture.playerOne(),
                "SALE", FIRST_CONFIRMATION, FIRST_CONFIRMATION.plusSeconds(11), 0, 10);

        assertThat(page.getContent()).extracting(TransaccionConfirmadaMercadoEntity::getId)
                .containsExactly(expected.getId());
    }

    @Test
    void paginaConservaTotalesYOrdenaPorFechaEIdCuandoElTimestampCoincide() {
        Fixture fixture = fixture();
        List<TransaccionConfirmadaMercadoEntity> saved = List.of(
                transaction(fixture, fixture.playerOne(), "Page one", "PURCHASE", 1L,
                        "1.00", "1.00", null, FIRST_CONFIRMATION),
                transaction(fixture, fixture.playerTwo(), "Page two", "PURCHASE", 1L,
                        "1.00", "1.00", null, FIRST_CONFIRMATION),
                transaction(fixture, fixture.playerOne(), "Page three", "PURCHASE", 1L,
                        "1.00", "1.00", null, FIRST_CONFIRMATION));
        List<UUID> expectedOrder = saved.stream()
                .map(TransaccionConfirmadaMercadoEntity::getId)
                .sorted(java.util.Comparator.comparing(UUID::toString).reversed())
                .toList();

        Page<TransaccionConfirmadaMercadoEntity> first =
                buscar(fixture.userId(), null, null, null, null, 0, 1);
        Page<TransaccionConfirmadaMercadoEntity> second =
                buscar(fixture.userId(), null, null, null, null, 1, 1);
        Page<TransaccionConfirmadaMercadoEntity> outside =
                buscar(fixture.userId(), null, null, null, null, 3, 1);

        assertThat(first.getContent()).extracting(TransaccionConfirmadaMercadoEntity::getId)
                .containsExactly(expectedOrder.get(0));
        assertThat(second.getContent()).extracting(TransaccionConfirmadaMercadoEntity::getId)
                .containsExactly(expectedOrder.get(1));
        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(first.getTotalPages()).isEqualTo(3);
        assertThat(outside.getContent()).isEmpty();
        assertThat(outside.getTotalElements()).isEqualTo(3);
        assertThat(outside.getTotalPages()).isEqualTo(3);
    }

    @Test
    void mantieneAislamientoDeUsuarioConFiltrosCombinados() {
        Fixture fixture = fixture();
        TransaccionConfirmadaMercadoEntity own = transaction(fixture, fixture.playerOne(),
                "Own transaction", "SALE", 1L, "2.00", "2.00", "0.50000000",
                FIRST_CONFIRMATION.plusSeconds(5));
        transaction(fixture, fixture.playerOne(), "Other user transaction", "SALE", 1L,
                "2.00", "2.00", "0.50000000", FIRST_CONFIRMATION.plusSeconds(5),
                fixture.otherUserId());

        Page<TransaccionConfirmadaMercadoEntity> page = buscar(fixture.userId(), fixture.playerOne(),
                "SALE", FIRST_CONFIRMATION, FIRST_CONFIRMATION.plusSeconds(6), 0, 10);

        assertThat(page.getContent()).extracting(TransaccionConfirmadaMercadoEntity::getId)
                .containsExactly(own.getId());
    }

    private Page<TransaccionConfirmadaMercadoEntity> buscar(UUID usuarioId, UUID jugadorId,
            String tipoOperacion, Instant desde, Instant hasta, int pagina, int limite) {
        return transacciones.buscar(usuarioId, jugadorId, tipoOperacion, desde, hasta,
                PageRequest.of(pagina, limite));
    }

    private Fixture fixture() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        usuarios.saveAndFlush(new UsuarioEntity(userId, "repo" + userId.toString().substring(0, 8),
                userId + "@example.test", HASH, FIRST_CONFIRMATION, FIRST_CONFIRMATION));
        usuarios.saveAndFlush(new UsuarioEntity(otherUserId,
                "repo" + otherUserId.toString().substring(0, 8), otherUserId + "@example.test",
                HASH, FIRST_CONFIRMATION, FIRST_CONFIRMATION));

        List<JugadorEntity> players = jugadores.findAll();
        assertThat(players).hasSizeGreaterThanOrEqualTo(2);
        UUID counterpartyId = contrapartes.findByTipoContraparte("SUPERUSER")
                .map(ContraparteMercadoEntity::getId)
                .orElseGet(() -> contrapartes.saveAndFlush(new ContraparteMercadoEntity(
                        UUID.randomUUID(), "SUPERUSER", "Mercado", FIRST_CONFIRMATION)).getId());
        return new Fixture(userId, otherUserId, players.get(0).getId(), players.get(1).getId(),
                counterpartyId);
    }

    private TransaccionConfirmadaMercadoEntity transaction(Fixture fixture, UUID playerId,
            String snapshot, String operation, Long quantity, String unitPrice, String totalAmount,
            String realizedPnl, Instant confirmedAt) {
        return transaction(fixture, playerId, snapshot, operation, quantity, unitPrice, totalAmount,
                realizedPnl, confirmedAt, fixture.userId());
    }

    private TransaccionConfirmadaMercadoEntity transaction(Fixture fixture, UUID playerId,
            String snapshot, String operation, Long quantity, String unitPrice, String totalAmount,
            String realizedPnl, Instant confirmedAt, UUID userId) {
        UUID quoteId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        cotizaciones.saveAndFlush(new VersionCotizacionMercadoEntity(
                new VersionCotizacionMercadoId(quoteId, 1L), playerId,
                new BigDecimal(unitPrice), FIRST_CONFIRMATION, FIRST_CONFIRMATION, null,
                "VALID", null, FIRST_CONFIRMATION));
        solicitudes.saveAndFlush(new SolicitudIdempotenciaMercadoEntity(requestId, userId,
                "key-" + requestId, operation, "parameters-" + requestId, "CONFIRMED",
                null, transactionId, confirmedAt, confirmedAt));
        TransaccionConfirmadaMercadoEntity transaction = new TransaccionConfirmadaMercadoEntity(
                transactionId, userId, playerId, snapshot, operation, quantity,
                new BigDecimal(unitPrice), new BigDecimal(totalAmount), fixture.counterpartyId(),
                quoteId, 1L, requestId,
                realizedPnl == null ? null : new BigDecimal(realizedPnl), confirmedAt);
        transacciones.saveAndFlush(transaction);
        return transaction;
    }

    @FunctionalInterface
    private interface TransactionWork<T> {
        T execute(Connection connection) throws Exception;
    }

    private static <T> T inTransaction(TransactionWork<T> work) throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword())) {
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (Exception | Error failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        }
    }

    private record Fixture(UUID userId, UUID otherUserId, UUID playerOne, UUID playerTwo,
            UUID counterpartyId) { }
}
