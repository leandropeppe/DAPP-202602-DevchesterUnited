package com.devchesterunited.devchesterunitedv2.persistence.market.repository;

import com.devchesterunited.devchesterunitedv2.persistence.market.entity.AsignacionCreditosInicialesEntity;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AsignacionCreditosInicialesRepository
        extends JpaRepository<AsignacionCreditosInicialesEntity, UUID> {
    Optional<AsignacionCreditosInicialesEntity> findByUsuarioIdAndTipoAsignacion(
            UUID usuarioId, String tipoAsignacion);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select asignacion from AsignacionCreditosInicialesEntity asignacion "
            + "where asignacion.usuarioId = :usuarioId "
            + "and asignacion.tipoAsignacion = :tipoAsignacion")
    Optional<AsignacionCreditosInicialesEntity> findByUsuarioIdAndTipoAsignacionForUpdate(
            @Param("usuarioId") UUID usuarioId,
            @Param("tipoAsignacion") String tipoAsignacion);
}
