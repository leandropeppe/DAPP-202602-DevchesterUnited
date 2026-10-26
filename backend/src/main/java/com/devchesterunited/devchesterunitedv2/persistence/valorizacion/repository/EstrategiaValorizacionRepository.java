package com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository;

import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.EstrategiaValorizacionEntity;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.EstrategiaValorizacionKey;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstrategiaValorizacionRepository extends JpaRepository<EstrategiaValorizacionEntity, EstrategiaValorizacionKey> {
    List<EstrategiaValorizacionEntity> findByHabilitadaParaCalculoTrue();
    Optional<EstrategiaValorizacionEntity> findByIdAndVersion(String id, int version);
}
