package com.devchesterunited.devchesterunitedv2.persistence.market.repository;

import com.devchesterunited.devchesterunitedv2.persistence.market.entity.TradeP2PEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TradeP2PRepository extends JpaRepository<TradeP2PEntity, UUID> {
    Optional<TradeP2PEntity> findBySolicitudIdempotenciaId(UUID solicitudIdempotenciaId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select trade from TradeP2PEntity trade where trade.id = :id")
    Optional<TradeP2PEntity> findByIdForRead(@Param("id") UUID id);

    List<TradeP2PEntity> findAllByOfertaIdOrderByConfirmadoEnDesc(UUID ofertaId);

    @Query("select trade from TradeP2PEntity trade "
            + "where trade.vendedorId = :usuarioId or trade.compradorId = :usuarioId "
            + "order by trade.confirmadoEn desc, trade.id desc")
    Page<TradeP2PEntity> findAllByUsuarioId(@Param("usuarioId") UUID usuarioId,
            Pageable pageable);
}
