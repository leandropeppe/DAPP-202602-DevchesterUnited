package com.devchesterunited.devchesterunitedv2.persistence.market.repository;

import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.JugadorEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.projection.MarketPlayerAvailabilityProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

/** Lectura agregada del mercado; no expone operaciones de escritura. */
public interface MarketAvailabilityRepository extends Repository<JugadorEntity, UUID> {
    @Query(value = """
            SELECT j.id AS "playerId",
                   j.name AS "playerName",
                   e.league AS "league",
                   e.name AS "teamName",
                   j.is_active AS "playerActive",
                   e.is_active AS "teamActive",
                   COALESCE(i.issued_quantity, 0) AS "issuedQuantity",
                   COALESCE(i.inventory_quantity, 0) AS "inventoryQuantity",
                   current_quote.quote_id AS "currentQuoteId",
                   current_quote.version AS "currentQuoteVersion",
                   current_quote.price_per_token AS "currentPricePerToken",
                   current_quote.strategy_reference AS "currentStrategyReference",
                   CASE WHEN h.validity_status = 'INVALIDATED' THEN 'INVALIDATED'
                        ELSE current_quote.validity_status END AS "currentValidityStatus",
                   current_quote.valid_from AS "currentValidFrom",
                   current_quote.valid_until AS "currentValidUntil",
                   latest_quote.quote_id AS "latestQuoteId",
                   latest_quote.version AS "latestQuoteVersion",
                   latest_quote.price_per_token AS "latestPricePerToken",
                   latest_quote.validity_status AS "latestValidityStatus",
                   latest_quote.valid_from AS "latestValidFrom",
                   latest_quote.valid_until AS "latestValidUntil"
              FROM jugadores j
              JOIN equipos e ON e.id = j.team_id
              LEFT JOIN market_token_issuances i ON i.player_id = j.id
              LEFT JOIN market_quote_headers h ON h.player_id = j.id
              LEFT JOIN market_quote_versions current_quote
                     ON current_quote.quote_id = h.quote_id
                    AND current_quote.version = h.current_version
                    AND current_quote.player_id = j.id
              LEFT JOIN LATERAL (
                    SELECT q.quote_id, q.version, q.price_per_token,
                           q.validity_status, q.valid_from, q.valid_until
                      FROM market_quote_versions q
                     WHERE q.player_id = j.id
                     ORDER BY q.published_at DESC, q.version DESC
                     LIMIT 1
              ) latest_quote ON TRUE
             WHERE j.id = :playerId
            """, nativeQuery = true)
    Optional<MarketPlayerAvailabilityProjection> findForMarket(UUID playerId);

    @Query(value = """
            SELECT j.id AS "playerId",
                   j.name AS "playerName",
                   e.league AS "league",
                   e.name AS "teamName",
                   j.is_active AS "playerActive",
                   e.is_active AS "teamActive",
                   COALESCE(i.issued_quantity, 0) AS "issuedQuantity",
                   COALESCE(i.inventory_quantity, 0) AS "inventoryQuantity",
                   current_quote.quote_id AS "currentQuoteId",
                   current_quote.version AS "currentQuoteVersion",
                   current_quote.price_per_token AS "currentPricePerToken",
                   current_quote.strategy_reference AS "currentStrategyReference",
                   CASE WHEN h.validity_status = 'INVALIDATED' THEN 'INVALIDATED'
                        ELSE current_quote.validity_status END AS "currentValidityStatus",
                   current_quote.valid_from AS "currentValidFrom",
                   current_quote.valid_until AS "currentValidUntil",
                   latest_quote.quote_id AS "latestQuoteId",
                   latest_quote.version AS "latestQuoteVersion",
                   latest_quote.price_per_token AS "latestPricePerToken",
                   latest_quote.validity_status AS "latestValidityStatus",
                   latest_quote.valid_from AS "latestValidFrom",
                   latest_quote.valid_until AS "latestValidUntil"
              FROM jugadores j
              JOIN equipos e ON e.id = j.team_id
              LEFT JOIN market_token_issuances i ON i.player_id = j.id
              LEFT JOIN market_quote_headers h ON h.player_id = j.id
              LEFT JOIN market_quote_versions current_quote
                     ON current_quote.quote_id = h.quote_id
                    AND current_quote.version = h.current_version
                    AND current_quote.player_id = j.id
              LEFT JOIN LATERAL (
                    SELECT q.quote_id, q.version, q.price_per_token,
                           q.validity_status, q.valid_from, q.valid_until
                      FROM market_quote_versions q
                     WHERE q.player_id = j.id
                     ORDER BY q.published_at DESC, q.version DESC
                     LIMIT 1
              ) latest_quote ON TRUE
             ORDER BY j.id ASC
            """,
            countQuery = """
            SELECT COUNT(*)
              FROM jugadores j
              JOIN equipos e ON e.id = j.team_id
            """,
            nativeQuery = true)
    Page<MarketPlayerAvailabilityProjection> findAllForMarket(Pageable pageable);
}
