package com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository;

import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.EstrategiaVisibleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EstrategiaVisibleRepository extends JpaRepository<EstrategiaVisibleEntity, Short> {
    default Optional<EstrategiaVisibleEntity> buscar() { return findById((short) 1); }
}
