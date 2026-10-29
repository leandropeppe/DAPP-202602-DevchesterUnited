package com.devchesterunited.devchesterunitedv2.persistence.market.repository;

import com.devchesterunited.devchesterunitedv2.persistence.market.entity.OfertaVentaP2PEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OfertaVentaP2PRepository extends JpaRepository<OfertaVentaP2PEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select oferta from OfertaVentaP2PEntity oferta where oferta.id = :id")
    Optional<OfertaVentaP2PEntity> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select oferta from OfertaVentaP2PEntity oferta "
            + "where oferta.id = :id and oferta.vendedorId = :vendedorId")
    Optional<OfertaVentaP2PEntity> findByIdAndVendedorIdForUpdate(
            @Param("id") UUID id, @Param("vendedorId") UUID vendedorId);

    @Query("select oferta from OfertaVentaP2PEntity oferta "
            + "where (:jugadorId is null or oferta.jugadorId = :jugadorId) "
            + "and (:estado is null or oferta.estado = :estado) "
            + "and (:soloMias = false or oferta.vendedorId = :vendedorId) "
            + "order by oferta.creadoEn desc, oferta.id desc")
    Page<OfertaVentaP2PEntity> buscar(@Param("jugadorId") UUID jugadorId,
            @Param("estado") String estado, @Param("soloMias") boolean soloMias,
            @Param("vendedorId") UUID vendedorId, Pageable pageable);

    Page<OfertaVentaP2PEntity> findAllByVendedorIdOrderByCreadoEnDescIdDesc(
            UUID vendedorId, Pageable pageable);
}
