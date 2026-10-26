package com.devchesterunited.devchesterunitedv2.persistence.valorizacion.repository;

import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.CotizacionActualEstrategiaEntity;
import com.devchesterunited.devchesterunitedv2.persistence.valorizacion.entity.CotizacionActualEstrategiaKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CotizacionActualEstrategiaRepository extends JpaRepository<CotizacionActualEstrategiaEntity, CotizacionActualEstrategiaKey> {
    List<CotizacionActualEstrategiaEntity> findByJugadorId(UUID jugadorId);
    java.util.Optional<CotizacionActualEstrategiaEntity> findByJugadorIdAndEstrategiaIdAndEstrategiaVersion(
            UUID jugadorId, String estrategiaId, int estrategiaVersion);
}
