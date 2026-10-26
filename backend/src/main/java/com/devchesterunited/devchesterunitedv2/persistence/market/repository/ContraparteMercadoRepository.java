package com.devchesterunited.devchesterunitedv2.persistence.market.repository;

import com.devchesterunited.devchesterunitedv2.persistence.market.entity.ContraparteMercadoEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ContraparteMercadoRepository
        extends JpaRepository<ContraparteMercadoEntity, UUID> {
    Optional<ContraparteMercadoEntity> findByTipoContraparte(String tipoContraparte);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select contraparte from ContraparteMercadoEntity contraparte "
            + "where contraparte.tipoContraparte = :tipoContraparte")
    Optional<ContraparteMercadoEntity> findByTipoContraparteForUpdate(
            @Param("tipoContraparte") String tipoContraparte);
}
