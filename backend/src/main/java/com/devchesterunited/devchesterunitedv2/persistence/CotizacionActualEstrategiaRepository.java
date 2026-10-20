package com.devchesterunited.devchesterunitedv2.persistence;

import com.devchesterunited.devchesterunitedv2.persistence.dto.CotizacionActualEstrategiaEntity;
import com.devchesterunited.devchesterunitedv2.persistence.dto.CotizacionActualEstrategiaKey;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CotizacionActualEstrategiaRepository extends JpaRepository<CotizacionActualEstrategiaEntity, CotizacionActualEstrategiaKey> {
    List<CotizacionActualEstrategiaEntity> findByJugadorId(UUID jugadorId);
    java.util.Optional<CotizacionActualEstrategiaEntity> findByJugadorIdAndEstrategiaIdAndEstrategiaVersion(
            UUID jugadorId, String estrategiaId, int estrategiaVersion);
}
