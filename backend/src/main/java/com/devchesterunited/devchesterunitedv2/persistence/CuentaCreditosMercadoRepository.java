package com.devchesterunited.devchesterunitedv2.persistence;

import com.devchesterunited.devchesterunitedv2.persistence.dto.CuentaCreditosMercadoEntity;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CuentaCreditosMercadoRepository
        extends JpaRepository<CuentaCreditosMercadoEntity, UUID> {
    Optional<CuentaCreditosMercadoEntity> findByUsuarioId(UUID usuarioId);
    Optional<CuentaCreditosMercadoEntity> findByContraparteId(UUID contraparteId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cuenta from CuentaCreditosMercadoEntity cuenta "
            + "where cuenta.usuarioId = :usuarioId")
    Optional<CuentaCreditosMercadoEntity> findByUsuarioIdForUpdate(
            @Param("usuarioId") UUID usuarioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cuenta from CuentaCreditosMercadoEntity cuenta "
            + "where cuenta.contraparteId = :contraparteId")
    Optional<CuentaCreditosMercadoEntity> findByContraparteIdForUpdate(
            @Param("contraparteId") UUID contraparteId);

    @Query("select cuenta from CuentaCreditosMercadoEntity cuenta "
            + "where cuenta.usuarioId = :usuarioId and cuenta.balance >= :montoMinimo")
    Optional<CuentaCreditosMercadoEntity> findByUsuarioIdWithBalanceAtLeast(
            @Param("usuarioId") UUID usuarioId, @Param("montoMinimo") BigDecimal montoMinimo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cuenta from CuentaCreditosMercadoEntity cuenta where cuenta.id = :id")
    Optional<CuentaCreditosMercadoEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("select cuenta from CuentaCreditosMercadoEntity cuenta "
            + "where cuenta.id = :id and cuenta.balance >= :montoMinimo")
    Optional<CuentaCreditosMercadoEntity> findByIdWithBalanceAtLeast(
            @Param("id") UUID id, @Param("montoMinimo") BigDecimal montoMinimo);
}
