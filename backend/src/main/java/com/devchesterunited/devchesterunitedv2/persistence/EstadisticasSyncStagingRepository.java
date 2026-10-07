package com.devchesterunited.devchesterunitedv2.persistence;

import com.devchesterunited.devchesterunitedv2.persistence.dto.EstadisticasSyncStagingEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstadisticasSyncStagingRepository
        extends JpaRepository<EstadisticasSyncStagingEntity, UUID> {
    List<EstadisticasSyncStagingEntity> findAllBySyncId(UUID syncId);
}
