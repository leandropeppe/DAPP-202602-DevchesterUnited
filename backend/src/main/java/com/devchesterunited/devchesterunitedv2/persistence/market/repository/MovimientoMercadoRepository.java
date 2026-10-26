package com.devchesterunited.devchesterunitedv2.persistence.market.repository;

import com.devchesterunited.devchesterunitedv2.persistence.market.entity.MovimientoMercadoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MovimientoMercadoRepository extends JpaRepository<MovimientoMercadoEntity, UUID> {
    List<MovimientoMercadoEntity> findAllByTransaccionIdOrderByCreadoEnAsc(UUID transaccionId);
}
