package com.devchesterunited.devchesterunitedv2.persistence;

import com.devchesterunited.devchesterunitedv2.persistence.dto.EstrategiaVisibleEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstrategiaVisibleRepository extends JpaRepository<EstrategiaVisibleEntity, Short> {
    default Optional<EstrategiaVisibleEntity> buscar() { return findById((short) 1); }
}
