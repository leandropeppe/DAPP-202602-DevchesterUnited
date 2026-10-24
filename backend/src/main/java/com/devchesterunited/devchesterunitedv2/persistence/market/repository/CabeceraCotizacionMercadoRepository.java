package com.devchesterunited.devchesterunitedv2.persistence.market.repository;

import com.devchesterunited.devchesterunitedv2.persistence.market.entity.CabeceraCotizacionMercadoEntity;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CabeceraCotizacionMercadoRepository
        extends JpaRepository<CabeceraCotizacionMercadoEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cabecera from CabeceraCotizacionMercadoEntity cabecera "
            + "where cabecera.jugadorId = :jugadorId")
    Optional<CabeceraCotizacionMercadoEntity> findByJugadorIdForUpdate(
            @Param("jugadorId") UUID jugadorId);
}
