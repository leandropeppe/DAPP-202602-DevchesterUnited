package com.devchesterunited.devchesterunitedv2.persistence.market.repository;

import com.devchesterunited.devchesterunitedv2.persistence.market.entity.EstadoMercadoEntity;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface EstadoMercadoRepository extends JpaRepository<EstadoMercadoEntity, Short> {
    @Modifying
    @Query(value = """
            INSERT INTO market_state (id, status, epoch, created_at, updated_at)
            VALUES (1, 'UNINITIALIZED', 1, :momento, :momento)
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    int ensureSingleton(@Param("momento") Instant momento);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select estado from EstadoMercadoEntity estado where estado.id = 1")
    Optional<EstadoMercadoEntity> findSingletonForUpdate();

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select estado from EstadoMercadoEntity estado where estado.id = 1")
    Optional<EstadoMercadoEntity> findSingletonForRead();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select estado from EstadoMercadoEntity estado where estado.id = :id")
    Optional<EstadoMercadoEntity> findByIdForUpdate(@Param("id") Short id);
}
