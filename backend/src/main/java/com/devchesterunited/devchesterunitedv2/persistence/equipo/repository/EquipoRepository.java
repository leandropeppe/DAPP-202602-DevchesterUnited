package com.devchesterunited.devchesterunitedv2.persistence.equipo.repository;

import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.EquipoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EquipoRepository extends JpaRepository<EquipoEntity, UUID> {
    List<EquipoEntity> findAllByActivoTrue();
    List<EquipoEntity> findAllByActivoTrueAndLigaIgnoreCase(String liga);
}
