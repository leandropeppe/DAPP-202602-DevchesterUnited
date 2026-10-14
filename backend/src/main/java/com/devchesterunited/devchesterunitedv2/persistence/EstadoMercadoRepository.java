package com.devchesterunited.devchesterunitedv2.persistence;

import com.devchesterunited.devchesterunitedv2.persistence.dto.EstadoMercadoEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface EstadoMercadoRepository extends JpaRepository<EstadoMercadoEntity, Short> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select estado from EstadoMercadoEntity estado where estado.id = :id")
    Optional<EstadoMercadoEntity> findByIdForUpdate(@Param("id") Short id);
}
