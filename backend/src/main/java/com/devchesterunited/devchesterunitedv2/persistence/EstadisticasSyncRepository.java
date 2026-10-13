package com.devchesterunited.devchesterunitedv2.persistence;

import com.devchesterunited.devchesterunitedv2.modelo.EstadoEstadisticasSync;
import com.devchesterunited.devchesterunitedv2.persistence.dto.EstadisticasSyncEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstadisticasSyncRepository extends JpaRepository<EstadisticasSyncEntity, UUID> {
    List<EstadisticasSyncEntity> findAllByEstado(EstadoEstadisticasSync estado);
}
