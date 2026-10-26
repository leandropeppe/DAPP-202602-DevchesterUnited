package com.devchesterunited.devchesterunitedv2.persistence.estadisticas.repository;

import com.devchesterunited.devchesterunitedv2.persistence.estadisticas.entity.EstadisticasJugadorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EstadisticasJugadorRepository extends JpaRepository<EstadisticasJugadorEntity, UUID> {
    Optional<EstadisticasJugadorEntity> findByJugadorId(UUID jugadorId);
}
