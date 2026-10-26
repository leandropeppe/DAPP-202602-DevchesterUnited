package com.devchesterunited.devchesterunitedv2.support.fixtures.market;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fixtures aislados y exclusivos de la demostracion controlada de US10.
 * No representa datos productivos ni publica cotizaciones por su cuenta.
 */
public final class MarketDemoFixtureBuilder {
    private static final String PASSWORD_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoO5V5f3n0e0D8n8f2h5x0M5K2Qf7tQm2S";

    private final JdbcTemplate jdbc;
    private final OffsetDateTime createdAt;

    public MarketDemoFixtureBuilder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.createdAt = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
    }

    public DemoFixture create() {
        UUID counterpartyId = findOrCreateSuperuser();
        ensureSuperuserAccount(counterpartyId);
        UUID teamId = jdbc.queryForObject("SELECT id FROM equipos ORDER BY id LIMIT 1",
                UUID.class);
        setMarketReady();

        List<DemoUser> users = new ArrayList<>();
        for (int index = 1; index <= 4; index++) {
            users.add(createUser(index));
        }

        List<DemoPlayer> players = new ArrayList<>();
        for (int index = 1; index <= 5; index++) {
            players.add(createPlayer(teamId, counterpartyId, index));
        }

        return new DemoFixture(List.copyOf(users), List.copyOf(players), counterpartyId);
    }

    private DemoUser createUser(int ordinal) {
        UUID userId = UUID.randomUUID();
        String suffix = userId.toString().replace("-", "");
        String username = "demo" + ordinal + suffix.substring(0, 24);
        jdbc.update("INSERT INTO usuarios "
                + "(id, username, email, password_hash, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)", userId, username,
                userId + "@demo.test", PASSWORD_HASH, createdAt, createdAt);
        jdbc.update("INSERT INTO market_credit_accounts "
                + "(id, user_id, counterparty_id, balance, created_at, updated_at) "
                + "VALUES (?, ?, NULL, 1000.00, ?, ?)", UUID.randomUUID(), userId,
                createdAt, createdAt);
        return new DemoUser(userId, username);
    }

    private DemoPlayer createPlayer(UUID teamId, UUID counterpartyId, int ordinal) {
        UUID playerId = UUID.randomUUID();
        jdbc.update("INSERT INTO jugadores "
                + "(id, name, position, jersey_number, nationality, team_id, is_active, "
                + "created_at, updated_at) VALUES (?, ?, 'Forward', ?, 'Argentina', ?, TRUE, ?, ?)",
                playerId, "T073 Demo Player " + ordinal, ordinal, teamId, createdAt, createdAt);
        jdbc.update("INSERT INTO market_token_issuances "
                + "(player_id, counterparty_id, issued_quantity, inventory_quantity, "
                + "created_at, updated_at) VALUES (?, ?, 100, 100, ?, ?)",
                playerId, counterpartyId, createdAt, createdAt);
        return new DemoPlayer(playerId, "T073 Demo Player " + ordinal);
    }

    private void setMarketReady() {
        jdbc.update("INSERT INTO market_state (id, status, epoch, created_at, updated_at) "
                + "VALUES (1, 'READY', 1, ?, ?) ON CONFLICT (id) DO UPDATE SET "
                + "status = 'READY', failure_reason = NULL, updated_at = EXCLUDED.updated_at",
                createdAt, createdAt);
    }

    private UUID findOrCreateSuperuser() {
        List<UUID> ids = jdbc.query("SELECT id FROM market_counterparties "
                        + "WHERE counterparty_type = 'SUPERUSER' LIMIT 1",
                (rs, row) -> rs.getObject(1, UUID.class));
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO market_counterparties "
                + "(id, counterparty_type, display_name, created_at) "
                + "VALUES (?, 'SUPERUSER', 'T073 Demo Superuser', ?)", id, createdAt);
        return id;
    }

    private void ensureSuperuserAccount(UUID counterpartyId) {
        List<UUID> ids = jdbc.query("SELECT id FROM market_credit_accounts "
                        + "WHERE counterparty_id = ? LIMIT 1",
                (rs, row) -> rs.getObject(1, UUID.class), counterpartyId);
        if (ids.isEmpty()) {
            jdbc.update("INSERT INTO market_credit_accounts "
                    + "(id, user_id, counterparty_id, balance, created_at, updated_at) "
                    + "VALUES (?, NULL, ?, 10000.00, ?, ?)", UUID.randomUUID(), counterpartyId,
                    createdAt, createdAt);
        } else {
            jdbc.update("UPDATE market_credit_accounts SET balance = 10000.00, updated_at = ? "
                    + "WHERE id = ?", createdAt, ids.get(0));
        }
    }

    public record DemoFixture(List<DemoUser> users, List<DemoPlayer> players,
            UUID counterpartyId) { }

    public record DemoUser(UUID id, String username) { }

    public record DemoPlayer(UUID id, String name) { }
}
