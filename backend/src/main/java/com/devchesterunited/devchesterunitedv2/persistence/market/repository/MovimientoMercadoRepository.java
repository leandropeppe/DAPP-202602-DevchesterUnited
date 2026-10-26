package com.devchesterunited.devchesterunitedv2.persistence.market.repository;

import com.devchesterunited.devchesterunitedv2.persistence.market.entity.MovimientoMercadoEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimientoMercadoRepository extends JpaRepository<MovimientoMercadoEntity, UUID> {
    List<MovimientoMercadoEntity> findAllByTransaccionIdOrderByCreadoEnAsc(UUID transaccionId);
}
