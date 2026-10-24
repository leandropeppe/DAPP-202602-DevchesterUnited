package com.devchesterunited.devchesterunitedv2.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.devchesterunited.devchesterunitedv2.configuration.PostgresTestSupport;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class TokenMarketInitializationPersistenceIntegrationTest extends PostgresTestSupport {
    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 10, 14, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final String PASSWORD_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoO5V5f3n0e0D8n8f2h5x0M5K2Qf7tQm2S";

    @Autowired
    private JdbcTemplate jdbc;

    private final List<Fixture> fixtures = new ArrayList<>();

    @AfterEach
    void limpiarFixturesPropios() {
        for (Fixture fixture : fixtures) {
            jdbc.update("DELETE FROM market_initial_credit_grants WHERE user_id = ?",
                    fixture.userId());
            jdbc.update("DELETE FROM market_token_issuances WHERE player_id = ?",
                    fixture.playerId());
            jdbc.update("DELETE FROM market_credit_accounts WHERE id = ?",
                    fixture.accountId());
            jdbc.update("DELETE FROM jugadores WHERE id = ?", fixture.playerId());
            jdbc.update("DELETE FROM usuarios WHERE id = ?", fixture.userId());
        }
        for (Fixture fixture : fixtures) {
            if (fixture.counterpartyCreated()) {
                jdbc.update("DELETE FROM market_counterparties WHERE id = ?",
                        fixture.counterpartyId());
            }
        }
        fixtures.clear();
    }

    @Test
    void impideDuplicarLaAsignacionInicialPorUsuarioYTipo() {
        Fixture fixture = fixture();
        insertAccount(fixture, "1000.00", null);
        insertGrant(fixture, "INITIAL_GRANT", "1000.00");

        assertSqlState("23505", connection -> insertGrant(connection, fixture,
                "INITIAL_GRANT", "1000.00"));
    }

    @Test
    void exigeElMontoExactoDeLaAsignacionInicial() {
        Fixture fixture = fixture();
        insertAccount(fixture, "1000.00", null);

        assertSqlState("23514", connection -> insertGrant(connection, fixture,
                "INITIAL_GRANT", "999.99"));
    }

    @Test
    void impideDuplicarLaEmisionYExigeCienTokensEmitidos() {
        Fixture fixture = fixture();
        insertIssuance(fixture, 100L, 100L);

        assertSqlState("23505", connection -> insertIssuance(connection, fixture, 100L, 100L));

        Fixture invalidQuantity = fixture();
        assertSqlState("23514", connection -> insertIssuance(connection, invalidQuantity, 99L, 99L));
    }

    @Test
    void limitaElInventarioEntreCeroYCantidadEmitida() {
        Fixture negative = fixture();
        assertSqlState("23514", connection -> insertIssuance(connection, negative, 100L, -1L));

        Fixture aboveIssued = fixture();
        assertSqlState("23514", connection -> insertIssuance(connection, aboveIssued, 100L, 101L));
    }

    @Test
    void impideSaldosNegativosYCuentasDuplicadasPorPropietario() {
        Fixture fixture = fixture();
        insertAccount(fixture, "100.00", null);

        Fixture duplicateUser = fixture.withSameUser();
        assertSqlState("23505", connection -> insertAccount(connection, duplicateUser,
                "50.00", null));

        Fixture duplicateCounterparty = fixture();
        ensureCounterpartyAccount(duplicateCounterparty, "100.00");
        Fixture secondCounterpartyAccount = duplicateCounterparty.withNewAccountId();
        assertSqlState("23505", connection -> insertCounterpartyAccount(connection,
                secondCounterpartyAccount, "50.00"));

        Fixture negativeBalance = fixture();
        assertSqlState("23514", connection -> insertAccount(connection, negativeBalance,
                "-0.01", null));
    }

    @Test
    void conservaRegistrosCorrectosEntreTransaccionesIndependientes() throws Exception {
        Fixture fixture = fixture();

        inTransaction(connection -> {
            insertAccount(connection, fixture, "1000.00", null);
            insertGrant(connection, fixture, "INITIAL_GRANT", "1000.00");
            insertIssuance(connection, fixture, 100L, 100L);
            return null;
        });

        Counts counts = inTransaction(connection -> new Counts(
                count(connection, "market_credit_accounts", fixture.accountId()),
                countByUserAndType(connection, fixture, "INITIAL_GRANT"),
                count(connection, "market_token_issuances", fixture.playerId())));

        assertThat(counts.accounts()).isEqualTo(1);
        assertThat(counts.grants()).isEqualTo(1);
        assertThat(counts.issuances()).isEqualTo(1);
    }

    @Test
    void reintentosDeInsercionConservanUnaSolaFilaTrasElRollback() throws Exception {
        Fixture fixture = fixture();
        inTransaction(connection -> {
            insertAccount(connection, fixture, "1000.00", null);
            insertGrant(connection, fixture, "INITIAL_GRANT", "1000.00");
            insertIssuance(connection, fixture, 100L, 100L);
            return null;
        });

        assertSqlState("23505", connection -> insertGrant(connection, fixture,
                "INITIAL_GRANT", "1000.00"));
        assertSqlState("23505", connection -> insertIssuance(connection, fixture, 100L, 100L));

        Counts counts = inTransaction(connection -> new Counts(
                count(connection, "market_credit_accounts", fixture.accountId()),
                countByUserAndType(connection, fixture, "INITIAL_GRANT"),
                count(connection, "market_token_issuances", fixture.playerId())));

        assertThat(counts.accounts()).isEqualTo(1);
        assertThat(counts.grants()).isEqualTo(1);
        assertThat(counts.issuances()).isEqualTo(1);
    }

    private Fixture fixture() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        UUID teamId = jdbc.queryForObject("SELECT id FROM equipos ORDER BY id LIMIT 1", UUID.class);

        jdbc.update("INSERT INTO usuarios "
                + "(id, username, email, password_hash, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)", userId,
                "init" + userId.toString().substring(0, 12), userId + "@example.test",
                PASSWORD_HASH, NOW, NOW);
        jdbc.update("INSERT INTO jugadores "
                + "(id, name, position, jersey_number, nationality, team_id, is_active, "
                + "created_at, updated_at) VALUES (?, ?, 'Forward', 99, 'Argentina', ?, TRUE, ?, ?)",
                playerId, "T017 Player " + playerId.toString().substring(0, 8), teamId, NOW, NOW);
        List<UUID> existingCounterparties = jdbc.query(
                "SELECT id FROM market_counterparties WHERE counterparty_type = 'SUPERUSER' LIMIT 1",
                (rs, row) -> rs.getObject(1, UUID.class));
        boolean counterpartyCreated = existingCounterparties.isEmpty();
        UUID counterpartyId = counterpartyCreated ? UUID.randomUUID() : existingCounterparties.get(0);
        if (counterpartyCreated) {
            jdbc.update("INSERT INTO market_counterparties "
                            + "(id, counterparty_type, display_name, created_at) "
                            + "VALUES (?, 'SUPERUSER', 'Mercado', ?)", counterpartyId, NOW);
        }
        Fixture fixture = new Fixture(userId, counterpartyId, accountId, playerId,
                counterpartyCreated);
        fixtures.add(fixture);
        return fixture;
    }

    private void insertAccount(Fixture fixture, String balance, UUID counterpartyId) {
        jdbc.update("INSERT INTO market_credit_accounts "
                + "(id, user_id, counterparty_id, balance, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)", fixture.accountId(), fixture.userId(),
                counterpartyId, new BigDecimal(balance), NOW, NOW);
    }

    private void insertCounterpartyAccount(Fixture fixture, String balance) {
        jdbc.update("INSERT INTO market_credit_accounts "
                + "(id, user_id, counterparty_id, balance, created_at, updated_at) "
                + "VALUES (?, NULL, ?, ?, ?, ?)", fixture.accountId(),
                fixture.counterpartyId(), new BigDecimal(balance), NOW, NOW);
    }

    private void ensureCounterpartyAccount(Fixture fixture, String balance) {
        List<UUID> existing = jdbc.queryForList(
                "SELECT id FROM market_credit_accounts WHERE counterparty_id = ? LIMIT 1",
                UUID.class, fixture.counterpartyId());
        if (existing.isEmpty()) {
            insertCounterpartyAccount(fixture, balance);
        }
    }

    private void insertGrant(Fixture fixture, String grantType, String amount) {
        jdbc.update("INSERT INTO market_initial_credit_grants "
                + "(id, user_id, credit_account_id, grant_type, amount, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)", UUID.randomUUID(), fixture.userId(),
                fixture.accountId(), grantType, new BigDecimal(amount), NOW);
    }

    private void insertIssuance(Fixture fixture, long issued, long inventory) {
        jdbc.update("INSERT INTO market_token_issuances "
                + "(player_id, counterparty_id, issued_quantity, inventory_quantity, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)", fixture.playerId(), fixture.counterpartyId(),
                issued, inventory, NOW, NOW);
    }

    private static void insertAccount(Connection connection, Fixture fixture, String balance,
            UUID counterpartyId) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO market_credit_accounts "
                + "(id, user_id, counterparty_id, balance, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, fixture.accountId());
            statement.setObject(2, fixture.userId());
            statement.setObject(3, counterpartyId);
            statement.setBigDecimal(4, new java.math.BigDecimal(balance));
            statement.setObject(5, NOW);
            statement.setObject(6, NOW);
            statement.executeUpdate();
        }
    }

    private static void insertCounterpartyAccount(Connection connection, Fixture fixture,
            String balance) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO market_credit_accounts "
                + "(id, user_id, counterparty_id, balance, created_at, updated_at) "
                + "VALUES (?, NULL, ?, ?, ?, ?)")) {
            statement.setObject(1, fixture.accountId());
            statement.setObject(2, fixture.counterpartyId());
            statement.setBigDecimal(3, new java.math.BigDecimal(balance));
            statement.setObject(4, NOW);
            statement.setObject(5, NOW);
            statement.executeUpdate();
        }
    }

    private static void insertGrant(Connection connection, Fixture fixture, String grantType,
            String amount) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO market_initial_credit_grants "
                + "(id, user_id, credit_account_id, grant_type, amount, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, fixture.userId());
            statement.setObject(3, fixture.accountId());
            statement.setString(4, grantType);
            statement.setBigDecimal(5, new java.math.BigDecimal(amount));
            statement.setObject(6, NOW);
            statement.executeUpdate();
        }
    }

    private static void insertIssuance(Connection connection, Fixture fixture, long issued,
            long inventory) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO market_token_issuances "
                + "(player_id, counterparty_id, issued_quantity, inventory_quantity, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, fixture.playerId());
            statement.setObject(2, fixture.counterpartyId());
            statement.setLong(3, issued);
            statement.setLong(4, inventory);
            statement.setObject(5, NOW);
            statement.setObject(6, NOW);
            statement.executeUpdate();
        }
    }

    private void assertSqlState(String expectedState, SqlOperation operation) {
        String actualState;
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                operation.execute(connection);
                connection.rollback();
                throw new AssertionError("La operación SQL debía fallar");
            } catch (SQLException failure) {
                actualState = failure.getSQLState();
                connection.rollback();
            }
        } catch (SQLException connectionFailure) {
            throw new AssertionError("No se pudo validar el SQLSTATE esperado", connectionFailure);
        } catch (Exception unexpectedFailure) {
            throw new AssertionError("La operación falló por una causa SQL inesperada",
                    unexpectedFailure);
        }

        assertThat(actualState).isEqualTo(expectedState);
    }

    private static long count(Connection connection, String table, UUID id) throws SQLException {
        String column = table.equals("market_token_issuances") ? "player_id" : "id";
        try (var statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?")) {
            statement.setObject(1, id);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static long countByUserAndType(Connection connection, Fixture fixture, String grantType)
            throws SQLException {
        try (var statement = connection.prepareStatement("SELECT COUNT(*) "
                + "FROM market_initial_credit_grants WHERE user_id = ? AND grant_type = ?")) {
            statement.setObject(1, fixture.userId());
            statement.setString(2, grantType);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private record Fixture(UUID userId, UUID counterpartyId, UUID accountId, UUID playerId,
            boolean counterpartyCreated) {
        private Fixture withSameUser() {
            return new Fixture(userId, UUID.randomUUID(), UUID.randomUUID(), playerId, false);
        }

        private Fixture withNewAccountId() {
            return new Fixture(userId, counterpartyId, UUID.randomUUID(), playerId, false);
        }
    }

    private record Counts(long accounts, long grants, long issuances) { }

    @FunctionalInterface
    private interface SqlOperation {
        void execute(Connection connection) throws Exception;
    }
}
