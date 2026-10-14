package com.devchesterunited.devchesterunitedv2.persistence;

import com.devchesterunited.devchesterunitedv2.persistence.dto.AuditoriaRechazoMercadoEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaRechazoMercadoRepository
        extends JpaRepository<AuditoriaRechazoMercadoEntity, UUID> {
    List<AuditoriaRechazoMercadoEntity> findAllByUsuarioIdOrderByCreadoEnDesc(UUID usuarioId);
}
