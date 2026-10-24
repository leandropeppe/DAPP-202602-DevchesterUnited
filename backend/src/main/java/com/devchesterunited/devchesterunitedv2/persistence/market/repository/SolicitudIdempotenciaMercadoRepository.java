package com.devchesterunited.devchesterunitedv2.persistence.market.repository;

import com.devchesterunited.devchesterunitedv2.persistence.market.entity.SolicitudIdempotenciaMercadoEntity;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SolicitudIdempotenciaMercadoRepository
        extends JpaRepository<SolicitudIdempotenciaMercadoEntity, UUID> {
    Optional<SolicitudIdempotenciaMercadoEntity> findByUsuarioIdAndClaveIdempotencia(
            UUID usuarioId, String claveIdempotencia);

    Optional<SolicitudIdempotenciaMercadoEntity> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select solicitud from SolicitudIdempotenciaMercadoEntity solicitud "
            + "where solicitud.id = :id")
    Optional<SolicitudIdempotenciaMercadoEntity> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select solicitud from SolicitudIdempotenciaMercadoEntity solicitud "
            + "where solicitud.usuarioId = :usuarioId "
            + "and solicitud.claveIdempotencia = :claveIdempotencia")
    Optional<SolicitudIdempotenciaMercadoEntity> findByUsuarioIdAndClaveForUpdate(
            @Param("usuarioId") UUID usuarioId,
            @Param("claveIdempotencia") String claveIdempotencia);
}
