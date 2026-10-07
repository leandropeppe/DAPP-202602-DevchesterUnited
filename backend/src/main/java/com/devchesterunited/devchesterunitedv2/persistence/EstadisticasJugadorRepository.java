package com.devchesterunited.devchesterunitedv2.persistence;

import com.devchesterunited.devchesterunitedv2.persistence.dto.EstadisticasJugadorEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstadisticasJugadorRepository extends JpaRepository<EstadisticasJugadorEntity, UUID> {
    Optional<EstadisticasJugadorEntity> findByJugadorId(UUID jugadorId);
}
