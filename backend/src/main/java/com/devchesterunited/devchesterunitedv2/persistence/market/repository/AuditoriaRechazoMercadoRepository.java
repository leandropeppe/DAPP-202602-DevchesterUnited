package com.devchesterunited.devchesterunitedv2.persistence.market.repository;

import com.devchesterunited.devchesterunitedv2.persistence.market.entity.AuditoriaRechazoMercadoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditoriaRechazoMercadoRepository
        extends JpaRepository<AuditoriaRechazoMercadoEntity, UUID> {
    List<AuditoriaRechazoMercadoEntity> findAllByUsuarioIdOrderByCreadoEnDesc(UUID usuarioId);
}
