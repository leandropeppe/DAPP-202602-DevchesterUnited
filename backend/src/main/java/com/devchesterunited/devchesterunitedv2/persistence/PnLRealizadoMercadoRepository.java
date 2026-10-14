package com.devchesterunited.devchesterunitedv2.persistence;

import com.devchesterunited.devchesterunitedv2.persistence.dto.PnLRealizadoMercadoEntity;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PnLRealizadoMercadoRepository
        extends JpaRepository<PnLRealizadoMercadoEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pnl from PnLRealizadoMercadoEntity pnl where pnl.usuarioId = :usuarioId")
    Optional<PnLRealizadoMercadoEntity> findByUsuarioIdForUpdate(
            @Param("usuarioId") UUID usuarioId);
}
