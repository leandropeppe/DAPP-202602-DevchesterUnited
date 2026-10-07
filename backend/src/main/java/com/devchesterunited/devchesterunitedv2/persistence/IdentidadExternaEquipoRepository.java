package com.devchesterunited.devchesterunitedv2.persistence;

import com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.dto.IdentidadExternaEquipoEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentidadExternaEquipoRepository
        extends JpaRepository<IdentidadExternaEquipoEntity, UUID> {
    Optional<IdentidadExternaEquipoEntity> findByEquipo_IdAndProveedor(
            UUID equipoId, ProveedorIdentidadExterna proveedor);

    Optional<IdentidadExternaEquipoEntity> findByProveedorAndExternalId(
            ProveedorIdentidadExterna proveedor, Long externalId);

    List<IdentidadExternaEquipoEntity> findAllByProveedorAndExternalIdIn(
            ProveedorIdentidadExterna proveedor, Collection<Long> externalIds);

    List<IdentidadExternaEquipoEntity> findAllByProveedor(ProveedorIdentidadExterna proveedor);
}
