package com.devchesterunited.devchesterunitedv2.persistence;

import com.devchesterunited.devchesterunitedv2.persistence.dto.TransaccionConfirmadaMercadoEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransaccionConfirmadaMercadoRepository
        extends JpaRepository<TransaccionConfirmadaMercadoEntity, UUID> {
    Optional<TransaccionConfirmadaMercadoEntity> findBySolicitudIdempotenciaId(
            UUID solicitudIdempotenciaId);

    Optional<TransaccionConfirmadaMercadoEntity> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    @Query("select transaccion from TransaccionConfirmadaMercadoEntity transaccion "
            + "where transaccion.usuarioId = :usuarioId "
            + "order by transaccion.confirmadoEn desc")
    List<TransaccionConfirmadaMercadoEntity> findAllByUsuarioIdOrderByConfirmadoEnDesc(
            @Param("usuarioId") UUID usuarioId);

    @Query("select transaccion from TransaccionConfirmadaMercadoEntity transaccion "
            + "where transaccion.usuarioId = :usuarioId "
            + "and transaccion.jugadorId = :jugadorId "
            + "order by transaccion.confirmadoEn desc")
    List<TransaccionConfirmadaMercadoEntity> findAllByUsuarioIdAndJugadorIdOrderByConfirmadoEnDesc(
            @Param("usuarioId") UUID usuarioId, @Param("jugadorId") UUID jugadorId);
}
