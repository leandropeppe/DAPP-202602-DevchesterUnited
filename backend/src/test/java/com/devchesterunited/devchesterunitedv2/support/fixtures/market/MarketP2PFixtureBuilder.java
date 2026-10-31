package com.devchesterunited.devchesterunitedv2.support.fixtures.market;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Fixture explícito para pruebas P2P; no inicia operaciones de negocio. */
public final class MarketP2PFixtureBuilder {
    private static final String PASSWORD_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoO5V5f3n0e0D8n8f2h5x0M5K2Qf7tQm2S";

    private final JdbcTemplate jdbc;
    private final OffsetDateTime now;

    public MarketP2PFixtureBuilder(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    public MarketP2PFixtureBuilder(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        if (clock == null) {
            throw new IllegalArgumentException("El reloj del fixture es obligatorio.");
        }
        this.now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    public FixtureUser createUser(String label, BigDecimal balance) {
        UUID id = UUID.randomUUID();
        String username = "p2p_" + label + "_" + id.toString().replace("-", "");
        jdbc.update("INSERT INTO usuarios "
                        + "(id, username, email, password_hash, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, username, id + "@p2p.test", PASSWORD_HASH, now, now);
        UUID accountId = UUID.randomUUID();
        jdbc.update("INSERT INTO market_credit_accounts "
                        + "(id, user_id, counterparty_id, balance, created_at, updated_at) "
                        + "VALUES (?, ?, NULL, ?, ?, ?)",
                accountId, id, balance, now, now);
        return new FixtureUser(id, accountId, username);
    }

    public FixturePlayer createPlayer(String name) {
        UUID id = UUID.randomUUID();
        UUID teamId = jdbc.queryForObject("SELECT id FROM equipos ORDER BY id LIMIT 1", UUID.class);
        jdbc.update("INSERT INTO jugadores "
                        + "(id, name, position, jersey_number, nationality, team_id, is_active, "
                        + "created_at, updated_at) VALUES (?, ?, 'Forward', 99, 'Argentina', ?, TRUE, ?, ?)",
                id, name, teamId, now, now);
        return new FixturePlayer(id, name);
    }

    public FixtureHolding createHolding(FixtureUser user, FixturePlayer player,
            long quantity, BigDecimal acquisitionCost) {
        jdbc.update("INSERT INTO market_holdings "
                        + "(user_id, player_id, quantity, acquisition_cost, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                user.id(), player.id(), quantity, acquisitionCost, now);
        return new FixtureHolding(user.id(), player.id(), quantity);
    }

    public FixtureQuote createQuote(FixturePlayer player, BigDecimal price, long version) {
        UUID quoteId = UUID.randomUUID();
        jdbc.update("INSERT INTO market_quote_versions "
                        + "(quote_id, version, player_id, price_per_token, published_at, "
                        + "valid_from, valid_until, validity_status, strategy_reference, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, NULL, 'VALID', 'P2P_TEST', ?)",
                quoteId, version, player.id(), price, now, now, now);
        jdbc.update("INSERT INTO market_quote_headers "
                        + "(player_id, quote_id, current_version, updated_at) VALUES (?, ?, ?, ?)",
                player.id(), quoteId, version, now);
        return new FixtureQuote(quoteId, version, player.id(), price);
    }

    public FixtureIdempotency createIdempotency(FixtureUser user, String key,
            String operation, String hash) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO market_idempotency_requests "
                        + "(id, user_id, idempotency_key, operation_type, parameters_hash, status, "
                        + "execution_token, result_reference, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'PROCESSING', ?, NULL, ?, ?)",
                id, user.id(), key, operation, hash, UUID.randomUUID(), now, now);
        return new FixtureIdempotency(id, user.id(), key);
    }

    public FixtureOffer createOffer(FixtureUser seller, FixturePlayer player,
            FixtureIdempotency idempotency, long quantity) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO market_p2p_offers "
                        + "(id, seller_user_id, player_id, original_quantity, remaining_quantity, "
                        + "sold_quantity, released_quantity, status, idempotency_request_id, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, 0, 0, 'OPEN', ?, ?, ?)",
                id, seller.id(), player.id(), quantity, quantity, idempotency.id(), now, now);
        return new FixtureOffer(id, seller.id(), player.id(), quantity);
    }

    public record FixtureUser(UUID id, UUID accountId, String username) { }

    public record FixturePlayer(UUID id, String name) { }

    public record FixtureHolding(UUID userId, UUID playerId, long quantity) { }

    public record FixtureQuote(UUID quoteId, long version, UUID playerId, BigDecimal price) { }

    public record FixtureIdempotency(UUID id, UUID userId, String key) { }

    public record FixtureOffer(UUID id, UUID sellerId, UUID playerId, long quantity) { }
}
