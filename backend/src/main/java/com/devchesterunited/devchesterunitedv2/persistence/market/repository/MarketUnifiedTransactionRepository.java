package com.devchesterunited.devchesterunitedv2.persistence.market.repository;

import com.devchesterunited.devchesterunitedv2.persistence.market.entity.TransaccionConfirmadaMercadoEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

/** Lectura paginada y unificada del historial personal. */
public interface MarketUnifiedTransactionRepository
        extends Repository<TransaccionConfirmadaMercadoEntity, UUID> {
    @Query(value = """
            SELECT unified.transaction_id AS transactionId,
                   unified.user_id AS userId,
                   unified.player_id AS playerId,
                   unified.player_name_snapshot AS playerNameSnapshot,
                   unified.operation_type AS operationType,
                   unified.quantity AS quantity,
                   unified.unit_price AS unitPrice,
                   unified.total_amount AS totalAmount,
                   unified.counterparty_id AS counterpartyId,
                   unified.quote_id AS quoteId,
                   unified.quote_version AS quoteVersion,
                   unified.idempotency_request_id AS idempotencyRequestId,
                   unified.realized_pnl AS realizedPnl,
                   unified.confirmed_at AS confirmedAt
            FROM (
                SELECT t.id AS transaction_id, t.user_id, t.player_id,
                       t.player_name_snapshot,
                       CASE WHEN t.operation_type = 'PURCHASE'
                            THEN 'PRIMARY_PURCHASE' ELSE t.operation_type END AS operation_type,
                       t.quantity, t.unit_price, t.total_amount, t.counterparty_id,
                       t.quote_id, t.quote_version, t.idempotency_request_id,
                       t.realized_pnl, t.confirmed_at
                FROM market_confirmed_transactions t
                WHERE t.user_id = :userId
                UNION ALL
                SELECT e.id AS transaction_id, e.user_id, tr.player_id,
                       tr.player_name_snapshot, e.entry_role AS operation_type,
                       e.quantity, tr.unit_price, tr.total_amount,
                       CASE WHEN e.entry_role = 'P2P_PURCHASE'
                            THEN tr.seller_user_id ELSE tr.buyer_user_id END AS counterparty_id,
                       tr.quote_id, tr.quote_version, tr.idempotency_request_id,
                       CASE WHEN e.entry_role = 'P2P_SALE'
                            THEN tr.realized_pnl ELSE NULL END AS realized_pnl,
                       e.created_at AS confirmed_at
                FROM market_p2p_trade_entries e
                JOIN market_p2p_trades tr ON tr.id = e.trade_id
                WHERE e.user_id = :userId
            ) unified
            WHERE (CAST(:playerId AS uuid) IS NULL
                   OR unified.player_id = CAST(:playerId AS uuid))
              AND (CAST(:operationType AS varchar) IS NULL
                   OR unified.operation_type = CAST(:operationType AS varchar)
                   OR (CAST(:operationType AS varchar) = 'PURCHASE'
                       AND unified.operation_type = 'PRIMARY_PURCHASE'))
              AND (CAST(:fromInstant AS timestamptz) IS NULL
                   OR unified.confirmed_at >= CAST(:fromInstant AS timestamptz))
              AND (CAST(:toInstant AS timestamptz) IS NULL
                   OR unified.confirmed_at < CAST(:toInstant AS timestamptz))
            ORDER BY unified.confirmed_at DESC, unified.transaction_id DESC
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM (
                SELECT t.id AS transaction_id, t.user_id, t.player_id,
                       t.player_name_snapshot,
                       CASE WHEN t.operation_type = 'PURCHASE'
                            THEN 'PRIMARY_PURCHASE' ELSE t.operation_type END AS operation_type,
                       t.quantity, t.unit_price, t.total_amount, t.counterparty_id,
                       t.quote_id, t.quote_version, t.idempotency_request_id,
                       t.realized_pnl, t.confirmed_at
                FROM market_confirmed_transactions t
                WHERE t.user_id = :userId
                UNION ALL
                SELECT e.id AS transaction_id, e.user_id, tr.player_id,
                       tr.player_name_snapshot, e.entry_role AS operation_type,
                       e.quantity, tr.unit_price, tr.total_amount,
                       CASE WHEN e.entry_role = 'P2P_PURCHASE'
                            THEN tr.seller_user_id ELSE tr.buyer_user_id END AS counterparty_id,
                       tr.quote_id, tr.quote_version, tr.idempotency_request_id,
                       CASE WHEN e.entry_role = 'P2P_SALE'
                            THEN tr.realized_pnl ELSE NULL END AS realized_pnl,
                       e.created_at AS confirmed_at
                FROM market_p2p_trade_entries e
                JOIN market_p2p_trades tr ON tr.id = e.trade_id
                WHERE e.user_id = :userId
            ) unified
            WHERE (CAST(:playerId AS uuid) IS NULL
                   OR unified.player_id = CAST(:playerId AS uuid))
              AND (CAST(:operationType AS varchar) IS NULL
                   OR unified.operation_type = CAST(:operationType AS varchar)
                   OR (CAST(:operationType AS varchar) = 'PURCHASE'
                       AND unified.operation_type = 'PRIMARY_PURCHASE'))
              AND (CAST(:fromInstant AS timestamptz) IS NULL
                   OR unified.confirmed_at >= CAST(:fromInstant AS timestamptz))
              AND (CAST(:toInstant AS timestamptz) IS NULL
                   OR unified.confirmed_at < CAST(:toInstant AS timestamptz))
            """,
            nativeQuery = true)
    Page<MarketUnifiedTransactionProjection> buscar(@Param("userId") UUID userId,
            @Param("playerId") UUID playerId, @Param("operationType") String operationType,
            @Param("fromInstant") Instant fromInstant, @Param("toInstant") Instant toInstant,
            Pageable pageable);
}
