package com.devchesterunited.devchesterunitedv2.persistence;

import com.devchesterunited.devchesterunitedv2.persistence.dto.TenenciaMercadoEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenenciaMercadoRepository
        extends JpaRepository<TenenciaMercadoEntity, com.devchesterunited.devchesterunitedv2.persistence.dto.TenenciaMercadoId> {
    @Query("select tenencia from TenenciaMercadoEntity tenencia "
            + "where tenencia.id.usuarioId = :usuarioId "
            + "and tenencia.id.jugadorId = :jugadorId")
    Optional<TenenciaMercadoEntity> findByUsuarioIdAndJugadorId(
            @Param("usuarioId") UUID usuarioId, @Param("jugadorId") UUID jugadorId);

    @Query("select tenencia from TenenciaMercadoEntity tenencia "
            + "where tenencia.id.usuarioId = :usuarioId "
            + "and tenencia.id.jugadorId = :jugadorId "
            + "and tenencia.quantity >= :cantidad")
    Optional<TenenciaMercadoEntity> findByUsuarioIdAndJugadorIdWithQuantityAtLeast(
            @Param("usuarioId") UUID usuarioId, @Param("jugadorId") UUID jugadorId,
            @Param("cantidad") Long cantidad);

    @Query("select tenencia from TenenciaMercadoEntity tenencia "
            + "where tenencia.id.usuarioId = :usuarioId")
    List<TenenciaMercadoEntity> findAllByUsuarioId(@Param("usuarioId") UUID usuarioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select tenencia from TenenciaMercadoEntity tenencia "
            + "where tenencia.id.usuarioId = :usuarioId "
            + "and tenencia.id.jugadorId = :jugadorId")
    Optional<TenenciaMercadoEntity> findByUsuarioIdAndJugadorIdForUpdate(
            @Param("usuarioId") UUID usuarioId, @Param("jugadorId") UUID jugadorId);
}
