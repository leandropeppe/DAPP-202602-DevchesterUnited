package com.devchesterunited.devchesterunitedv2.persistence.market.repository;

import com.devchesterunited.devchesterunitedv2.persistence.market.entity.MarketP2PMovementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MarketP2PMovementRepository
        extends JpaRepository<MarketP2PMovementEntity, UUID> {
    List<MarketP2PMovementEntity> findAllByTradeIdOrderByRoleAsc(UUID tradeId);
}
