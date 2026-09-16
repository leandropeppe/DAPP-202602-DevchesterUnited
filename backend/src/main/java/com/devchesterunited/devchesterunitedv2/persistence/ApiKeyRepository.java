package com.devchesterunited.devchesterunitedv2.persistence;

import com.devchesterunited.devchesterunitedv2.persistence.dto.APIKeyEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<APIKeyEntity, UUID> {
    Optional<APIKeyEntity> findByHashClave(String hashClave);

    Optional<APIKeyEntity> findByHashClaveAndActivaTrue(String hashClave);
    Optional<APIKeyEntity> findByUsuarioIdAndActivaTrue(UUID usuarioId);
}
