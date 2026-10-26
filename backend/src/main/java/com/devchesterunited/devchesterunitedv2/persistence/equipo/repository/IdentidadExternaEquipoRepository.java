package com.devchesterunited.devchesterunitedv2.persistence.equipo.repository;

import com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.equipo.entity.IdentidadExternaEquipoEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdentidadExternaEquipoRepository
        extends JpaRepository<IdentidadExternaEquipoEntity, UUID> {
    Optional<IdentidadExternaEquipoEntity> findByEquipo_IdAndProveedor(
            UUID equipoId, ProveedorIdentidadExterna proveedor);

    @Query("select identidad from IdentidadExternaEquipoEntity identidad "
            + "join fetch identidad.equipo "
            + "where identidad.proveedor = :proveedor and identidad.externalId = :externalId")
    Optional<IdentidadExternaEquipoEntity> findByProveedorAndExternalId(
            @Param("proveedor") ProveedorIdentidadExterna proveedor,
            @Param("externalId") Long externalId);

    List<IdentidadExternaEquipoEntity> findAllByProveedorAndExternalIdIn(
            ProveedorIdentidadExterna proveedor, Collection<Long> externalIds);

    List<IdentidadExternaEquipoEntity> findAllByProveedor(ProveedorIdentidadExterna proveedor);
}
