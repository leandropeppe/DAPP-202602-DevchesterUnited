package com.devchesterunited.devchesterunitedv2.persistence;

import com.devchesterunited.devchesterunitedv2.persistence.dto.EquipoEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipoRepository extends JpaRepository<EquipoEntity, UUID> {
    List<EquipoEntity> findAllByActivoTrue();
}
