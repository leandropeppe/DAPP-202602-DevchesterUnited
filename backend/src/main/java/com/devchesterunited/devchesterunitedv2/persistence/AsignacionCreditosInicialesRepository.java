package com.devchesterunited.devchesterunitedv2.persistence;

import com.devchesterunited.devchesterunitedv2.persistence.dto.AsignacionCreditosInicialesEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AsignacionCreditosInicialesRepository
        extends JpaRepository<AsignacionCreditosInicialesEntity, UUID> {
    Optional<AsignacionCreditosInicialesEntity> findByUsuarioIdAndTipoAsignacion(
            UUID usuarioId, String tipoAsignacion);
}
