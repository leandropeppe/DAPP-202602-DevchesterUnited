package com.devchesterunited.devchesterunitedv2.persistence.jugador.repository;

import com.devchesterunited.devchesterunitedv2.modelo.jugador.ProveedorIdentidadExterna;
import com.devchesterunited.devchesterunitedv2.persistence.jugador.entity.IdentidadExternaJugadorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IdentidadExternaJugadorRepository
        extends JpaRepository<IdentidadExternaJugadorEntity, UUID> {
    Optional<IdentidadExternaJugadorEntity> findByJugador_IdAndProveedor(
            UUID jugadorId, ProveedorIdentidadExterna proveedor);

    @Query("select identidad from IdentidadExternaJugadorEntity identidad "
            + "join fetch identidad.jugador "
            + "where identidad.proveedor = :proveedor and identidad.externalId = :externalId")
    Optional<IdentidadExternaJugadorEntity> findByProveedorAndExternalId(
            @Param("proveedor") ProveedorIdentidadExterna proveedor,
            @Param("externalId") Long externalId);

    List<IdentidadExternaJugadorEntity> findAllByProveedorAndExternalIdIn(
            ProveedorIdentidadExterna proveedor, Collection<Long> externalIds);

    List<IdentidadExternaJugadorEntity> findAllByProveedor(ProveedorIdentidadExterna proveedor);
}
