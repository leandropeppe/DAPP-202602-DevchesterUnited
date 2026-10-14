package com.devchesterunited.devchesterunitedv2.persistence;

import com.devchesterunited.devchesterunitedv2.persistence.dto.ContraparteMercadoEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContraparteMercadoRepository
        extends JpaRepository<ContraparteMercadoEntity, UUID> {
    Optional<ContraparteMercadoEntity> findByTipoContraparte(String tipoContraparte);
}
