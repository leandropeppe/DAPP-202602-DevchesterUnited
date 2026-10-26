package com.devchesterunited.devchesterunitedv2.persistence.market.repository;

import com.devchesterunited.devchesterunitedv2.persistence.market.entity.EmisionTokensMercadoEntity;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmisionTokensMercadoRepository
        extends JpaRepository<EmisionTokensMercadoEntity, UUID> {
    Optional<EmisionTokensMercadoEntity> findByJugadorId(UUID jugadorId);

    @Query("select emision from EmisionTokensMercadoEntity emision "
            + "where emision.jugadorId = :jugadorId "
            + "and emision.cantidadInventario >= :cantidad")
    Optional<EmisionTokensMercadoEntity> findByJugadorIdWithInventoryAtLeast(
            @Param("jugadorId") UUID jugadorId, @Param("cantidad") Long cantidad);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select emision from EmisionTokensMercadoEntity emision "
            + "where emision.jugadorId = :jugadorId")
    Optional<EmisionTokensMercadoEntity> findByJugadorIdForUpdate(
            @Param("jugadorId") UUID jugadorId);
}
