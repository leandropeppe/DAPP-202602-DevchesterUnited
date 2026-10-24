package com.devchesterunited.devchesterunitedv2.persistence;
import com.devchesterunited.devchesterunitedv2.persistence.auth.repository.UsuarioRepository;

import static org.assertj.core.api.Assertions.assertThat;

import com.devchesterunited.devchesterunitedv2.support.PostgresTestSupport;
import com.devchesterunited.devchesterunitedv2.persistence.auth.entity.UsuarioEntity;
import com.devchesterunited.devchesterunitedv2.service.dto.market.MarketRejectedAttempt;
import com.devchesterunited.devchesterunitedv2.service.interfaces.market.MarketRejectionFinalizationService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verifica con PostgreSQL la atomicidad de la finalización interna de T062. */
@SpringBootTest(properties = "token-market.initialization.auto-start=false")
class MarketRejectionFinalizationIntegrationTest extends PostgresTestSupport {
    private static final String PASSWORD_HASH =
            "$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW";
    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private MarketRejectionFinalizationService finalization;

    private UUID testUser;

    @AfterEach
    void cleanFixtures() {
        if (testUser != null) {
            jdbc.update("DELETE FROM market_rejected_attempts WHERE user_id = ?", testUser);
            jdbc.update("DELETE FROM market_idempotency_requests WHERE user_id = ?", testUser);
            jdbc.update("DELETE FROM usuarios WHERE id = ?", testUser);
            testUser = null;
        }
    }

    @Test
    void processingConTokenValidoTransicionaYAuditaUnaVez() {
        UUID token = UUID.randomUUID();
        UUID requestId = insertRequest("PROCESSING", token);

        var result = finalization.finalizarRechazo(requestId, token, attempt());

        assertThat(result.transicionRealizada()).isTrue();
        assertThat(status(requestId)).isEqualTo("REJECTED");
        assertThat(auditCount()).isOne();
        assertThat(confirmedCount()).isZero();
        assertThat(movementCount()).isZero();
    }

    @Test
    void tokenIncorrectoNoTransicionaNiAudita() {
        UUID token = UUID.randomUUID();
        UUID requestId = insertRequest("PROCESSING", token);

        finalization.finalizarRechazo(requestId, UUID.randomUUID(), attempt());

        assertThat(status(requestId)).isEqualTo("PROCESSING");
        assertThat(auditCount()).isZero();
    }

    @Test
    void estadosFinalesEInciertosNoSeModificanNiDuplicanAuditoria() {
        UUID confirmed = insertRequest("CONFIRMED", null);
        UUID rejected = insertRequest("REJECTED", null);
        UUID uncertain = insertRequest("UNCERTAIN", null);
        jdbc.update("INSERT INTO market_rejected_attempts "
                        + "(id, user_id, operation_type, player_id, idempotency_key_hash, "
                        + "reason_code, parameters_hash, created_at) "
                        + "VALUES (?, ?, 'PURCHASE', NULL, NULL, 'INVALID_INPUT', NULL, ?)",
                UUID.randomUUID(), testUser, Timestamp.from(NOW));

        finalization.finalizarRechazo(confirmed, UUID.randomUUID(), attempt());
        finalization.finalizarRechazo(rejected, UUID.randomUUID(), attempt());
        finalization.finalizarRechazo(uncertain, UUID.randomUUID(), attempt());

        assertThat(status(confirmed)).isEqualTo("CONFIRMED");
        assertThat(status(rejected)).isEqualTo("REJECTED");
        assertThat(status(uncertain)).isEqualTo("UNCERTAIN");
        assertThat(auditCount()).isOne();
    }

    @Test
    void falloDeAuditoriaRevierteTambienLaTransicion() {
        UUID token = UUID.randomUUID();
        UUID requestId = insertRequest("PROCESSING", token);
        MarketRejectedAttempt invalidForeignKey = new MarketRejectedAttempt(
                UUID.randomUUID(), "PURCHASE", null, null, "INVALID_INPUT", null, NOW);

        boolean failed = false;
        try {
            finalization.finalizarRechazo(requestId, token, invalidForeignKey);
        } catch (RuntimeException expected) {
            failed = true;
        }

        assertThat(failed).isTrue();
        assertThat(status(requestId)).isEqualTo("PROCESSING");
        assertThat(auditCount()).isZero();
    }

    private UUID insertRequest(String status, UUID token) {
        ensureUser();
        UUID requestId = UUID.randomUUID();
        jdbc.update("INSERT INTO market_idempotency_requests "
                        + "(id, user_id, idempotency_key, operation_type, parameters_hash, status, "
                        + "execution_token, result_reference, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'PURCHASE', ?, ?, ?, NULL, ?, ?)",
                requestId, testUser, "key-" + requestId, "a".repeat(64), status, token,
                Timestamp.from(NOW), Timestamp.from(NOW));
        return requestId;
    }

    private void ensureUser() {
        if (testUser != null) {
            return;
        }
        testUser = UUID.randomUUID();
        String username = "t062-" + testUser.toString().replace("-", "");
        usuarios.saveAndFlush(new UsuarioEntity(testUser, username, username + "@example.com",
                PASSWORD_HASH, NOW, NOW));
    }

    private MarketRejectedAttempt attempt() {
        return new MarketRejectedAttempt(testUser, "PURCHASE", null,
                "b".repeat(64), "INVALID_INPUT", "a".repeat(64), NOW);
    }

    private String status(UUID requestId) {
        return jdbc.queryForObject("SELECT status FROM market_idempotency_requests WHERE id = ?",
                String.class, requestId);
    }

    private long auditCount() {
        return jdbc.queryForObject("SELECT count(*) FROM market_rejected_attempts WHERE user_id = ?",
                Long.class, testUser);
    }

    private long confirmedCount() {
        return jdbc.queryForObject("SELECT count(*) FROM market_confirmed_transactions WHERE user_id = ?",
                Long.class, testUser);
    }

    private long movementCount() {
        return jdbc.queryForObject("SELECT count(*) FROM market_movements movement "
                        + "JOIN market_confirmed_transactions transaction_record "
                        + "ON transaction_record.id = movement.transaction_id "
                        + "WHERE transaction_record.user_id = ?", Long.class, testUser);
    }
}
