package com.devchesterunited.devchesterunitedv2.persistence.market.repository;

import com.devchesterunited.devchesterunitedv2.persistence.market.entity.AsientoP2PEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AsientoP2PRepository extends JpaRepository<AsientoP2PEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select asiento from AsientoP2PEntity asiento where asiento.tradeId = :tradeId")
    List<AsientoP2PEntity> findAllByTradeIdForRead(@Param("tradeId") UUID tradeId);

    Page<AsientoP2PEntity> findAllByUsuarioIdOrderByCreadoEnDescIdDesc(
            UUID usuarioId, Pageable pageable);
}
