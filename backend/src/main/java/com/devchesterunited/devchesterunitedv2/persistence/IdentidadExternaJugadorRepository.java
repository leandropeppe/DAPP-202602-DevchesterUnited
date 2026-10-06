package com.devchesterunited.devchesterunitedv2.persistence;

import com.devchesterunited.devchesterunitedv2.modelo.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.dto.IdentidadExternaJugadorEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentidadExternaJugadorRepository
        extends JpaRepository<IdentidadExternaJugadorEntity, UUID> {
    Optional<IdentidadExternaJugadorEntity> findByJugador_IdAndProveedor(
            UUID jugadorId, ProveedorIdentidadExterna proveedor);

    Optional<IdentidadExternaJugadorEntity> findByProveedorAndExternalId(
            ProveedorIdentidadExterna proveedor, Long externalId);

    List<IdentidadExternaJugadorEntity> findAllByProveedorAndExternalIdIn(
            ProveedorIdentidadExterna proveedor, Collection<Long> externalIds);

    List<IdentidadExternaJugadorEntity> findAllByProveedor(ProveedorIdentidadExterna proveedor);
}
