package com.devchesterunited.devchesterunitedv2.persistence;

import com.devchesterunited.devchesterunitedv2.persistence.dto.UsuarioEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Recibe identidades ya normalizadas por la capa de entrada. */
public interface UsuarioRepository extends JpaRepository<UsuarioEntity, UUID> {
    Optional<UsuarioEntity> findByNombreUsuario(String nombreUsuario);
    Optional<UsuarioEntity> findByCorreo(String correo);

    @Query(value = "SELECT * FROM usuarios WHERE id = :id FOR UPDATE NOWAIT", nativeQuery = true)
    Optional<UsuarioEntity> findByIdForUpdateNoWait(@org.springframework.data.repository.query.Param("id") UUID id);
}
