package com.devchesterunited.devchesterunitedv2.persistence.market.repository;

import com.devchesterunited.devchesterunitedv2.persistence.market.entity.AuditoriaCancelacionOfertaP2PEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AuditoriaCancelacionOfertaP2PRepository
        extends JpaRepository<AuditoriaCancelacionOfertaP2PEntity, UUID> {
    Optional<AuditoriaCancelacionOfertaP2PEntity> findByOfertaId(UUID ofertaId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select auditoria from AuditoriaCancelacionOfertaP2PEntity auditoria "
            + "where auditoria.ofertaId = :ofertaId")
    Optional<AuditoriaCancelacionOfertaP2PEntity> findByOfertaIdForRead(
            @Param("ofertaId") UUID ofertaId);
}
