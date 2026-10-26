package com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository;

import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.HistoricoCotizacionEstrategiaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoricoCotizacionEstrategiaRepository extends JpaRepository<HistoricoCotizacionEstrategiaEntity, UUID> {
    List<HistoricoCotizacionEstrategiaEntity> findByJugadorIdOrderByCalculadaAtDesc(UUID jugadorId);
    List<HistoricoCotizacionEstrategiaEntity> findByJugadorIdAndEstrategiaIdAndEstrategiaVersionOrderByCalculadaAtDesc(UUID jugadorId, String estrategiaId, int version);
}
