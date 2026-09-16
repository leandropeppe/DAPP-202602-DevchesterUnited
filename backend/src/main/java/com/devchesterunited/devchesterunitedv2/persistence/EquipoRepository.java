package com.devchesterunited.devchesterunitedv2.persistence;

import com.devchesterunited.devchesterunitedv2.persistence.dto.EquipoEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipoRepository extends JpaRepository<EquipoEntity, UUID> {
    Optional<EquipoEntity> findByIdExterno(Long idExterno);
    List<EquipoEntity> findAllByIdExternoIn(Collection<Long> idsExternos);
    List<EquipoEntity> findAllByActivoTrue();
}
