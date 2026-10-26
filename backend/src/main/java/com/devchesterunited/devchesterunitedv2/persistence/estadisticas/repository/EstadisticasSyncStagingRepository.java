package com.devchesterunited.devchesterunitedv2.persistence.estadisticas.repository;

import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.entity.EstadisticasSyncStagingEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EstadisticasSyncStagingRepository
        extends JpaRepository<EstadisticasSyncStagingEntity, UUID> {
    List<EstadisticasSyncStagingEntity> findAllBySyncId(UUID syncId);

    java.util.Optional<EstadisticasSyncStagingEntity> findBySyncIdAndCompeticionAndWhoscoredPlayerId(
            UUID syncId, String competicion, Long whoscoredPlayerId);

    Page<EstadisticasSyncStagingEntity> findAllBySyncId(UUID syncId, Pageable pageable);
}
