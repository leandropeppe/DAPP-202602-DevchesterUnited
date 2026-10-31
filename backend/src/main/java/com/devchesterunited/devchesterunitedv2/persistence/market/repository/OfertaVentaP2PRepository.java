package com.devchesterunited.devchesterunitedv2.persistence.market.repository;

import com.devchesterunited.devchesterunitedv2.persistence.market.entity.OfertaVentaP2PEntity;
import com.devchesterunited.devchesterunitedv2.persistence.market.projection.MarketP2POfferProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OfertaVentaP2PRepository extends JpaRepository<OfertaVentaP2PEntity, UUID> {
    @Query(value = """
            SELECT o.id AS offer_id,
                   o.player_id AS player_id,
                   o.seller_user_id AS seller_id,
                   u.username AS seller_alias,
                   o.remaining_quantity AS remaining_quantity,
                   o.status AS persisted_status,
                   j.is_active AS player_active,
                   h.validity_status AS header_validity_status,
                   q.quote_id AS quote_id,
                   q.version AS quote_version,
                   q.price_per_token AS unit_price,
                   q.published_at AS published_at,
                   q.valid_from AS valid_from,
                   q.valid_until AS valid_until,
                   q.validity_status AS quote_validity_status,
                   q.strategy_reference AS strategy_reference
              FROM market_p2p_offers o
              JOIN jugadores j ON j.id = o.player_id
              JOIN usuarios u ON u.id = o.seller_user_id
              LEFT JOIN market_quote_headers h ON h.player_id = o.player_id
              LEFT JOIN market_quote_versions q
                     ON q.quote_id = h.quote_id
                    AND q.version = h.current_version
                    AND q.player_id = h.player_id
             WHERE (:jugadorId IS NULL OR o.player_id = :jugadorId)
               AND (:soloMias = false OR o.seller_user_id = :vendedorId)
               AND (
                    :estado IS NULL
                    OR (:estado IN ('OPEN', 'PARTIALLY_FILLED', 'FILLED', 'CANCELLED')
                        AND o.status = :estado)
                    OR (:estado = 'PURCHASABLE'
                        AND o.status IN ('OPEN', 'PARTIALLY_FILLED')
                        AND j.is_active = true
                        AND h.validity_status = 'VALID'
                        AND q.validity_status = 'VALID'
                        AND q.valid_from <= :ahora
                        AND (q.valid_until IS NULL OR :ahora < q.valid_until))
                    OR (:estado = 'SUSPENDED'
                        AND o.status IN ('OPEN', 'PARTIALLY_FILLED')
                        AND COALESCE((
                            j.is_active = true
                            AND h.validity_status = 'VALID'
                            AND q.validity_status = 'VALID'
                            AND q.valid_from <= :ahora
                            AND (q.valid_until IS NULL OR :ahora < q.valid_until)
                        ), FALSE) = FALSE
               ))
             ORDER BY o.created_at DESC, o.id DESC
            """,
            countQuery = """
            SELECT COUNT(*)
              FROM market_p2p_offers o
              JOIN jugadores j ON j.id = o.player_id
              JOIN usuarios u ON u.id = o.seller_user_id
              LEFT JOIN market_quote_headers h ON h.player_id = o.player_id
              LEFT JOIN market_quote_versions q
                     ON q.quote_id = h.quote_id
                    AND q.version = h.current_version
                    AND q.player_id = h.player_id
             WHERE (:jugadorId IS NULL OR o.player_id = :jugadorId)
               AND (:soloMias = false OR o.seller_user_id = :vendedorId)
               AND (
                    :estado IS NULL
                    OR (:estado IN ('OPEN', 'PARTIALLY_FILLED', 'FILLED', 'CANCELLED')
                        AND o.status = :estado)
                    OR (:estado = 'PURCHASABLE'
                        AND o.status IN ('OPEN', 'PARTIALLY_FILLED')
                        AND j.is_active = true
                        AND h.validity_status = 'VALID'
                        AND q.validity_status = 'VALID'
                        AND q.valid_from <= :ahora
                        AND (q.valid_until IS NULL OR :ahora < q.valid_until))
                    OR (:estado = 'SUSPENDED'
                        AND o.status IN ('OPEN', 'PARTIALLY_FILLED')
                        AND COALESCE((
                            j.is_active = true
                            AND h.validity_status = 'VALID'
                            AND q.validity_status = 'VALID'
                            AND q.valid_from <= :ahora
                            AND (q.valid_until IS NULL OR :ahora < q.valid_until)
                        ), FALSE) = FALSE
               ))
            """, nativeQuery = true)
    Page<MarketP2POfferProjection> buscarPublico(
            @Param("jugadorId") UUID jugadorId,
            @Param("estado") String estado,
            @Param("soloMias") boolean soloMias,
            @Param("vendedorId") UUID vendedorId,
            @Param("ahora") java.time.Instant ahora,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select oferta from OfertaVentaP2PEntity oferta where oferta.id = :id")
    Optional<OfertaVentaP2PEntity> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select oferta from OfertaVentaP2PEntity oferta "
            + "where oferta.id = :id and oferta.vendedorId = :vendedorId")
    Optional<OfertaVentaP2PEntity> findByIdAndVendedorIdForUpdate(
            @Param("id") UUID id, @Param("vendedorId") UUID vendedorId);

    @Query("select oferta from OfertaVentaP2PEntity oferta "
            + "where (:jugadorId is null or oferta.jugadorId = :jugadorId) "
            + "and (:estado is null or oferta.estado = :estado) "
            + "and (:soloMias = false or oferta.vendedorId = :vendedorId) "
            + "order by oferta.creadoEn desc, oferta.id desc")
    Page<OfertaVentaP2PEntity> buscar(@Param("jugadorId") UUID jugadorId,
            @Param("estado") String estado, @Param("soloMias") boolean soloMias,
            @Param("vendedorId") UUID vendedorId, Pageable pageable);

    Page<OfertaVentaP2PEntity> findAllByVendedorIdOrderByCreadoEnDescIdDesc(
            UUID vendedorId, Pageable pageable);

    @Query("select coalesce(sum(oferta.cantidadRestante), 0) "
            + "from OfertaVentaP2PEntity oferta "
            + "where oferta.vendedorId = :vendedorId and oferta.jugadorId = :jugadorId "
            + "and oferta.estado in ('OPEN', 'PARTIALLY_FILLED')")
    Long sumRemainingActiveByVendedorIdAndJugadorId(@Param("vendedorId") UUID vendedorId,
            @Param("jugadorId") UUID jugadorId);
}
