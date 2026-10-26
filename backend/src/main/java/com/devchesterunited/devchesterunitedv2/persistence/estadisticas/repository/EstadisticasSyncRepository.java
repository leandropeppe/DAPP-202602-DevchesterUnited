package com.devchesterunited.devchesterunitedv2.persistence.estadisticas.repository;

import com.devchesterunited.devchesterunitedv2.modelo.estadisticas.EstadoEstadisticasSync;
import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.entity.EstadisticasSyncEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EstadisticasSyncRepository extends JpaRepository<EstadisticasSyncEntity, UUID> {
    List<EstadisticasSyncEntity> findAllByEstado(EstadoEstadisticasSync estado);
}
