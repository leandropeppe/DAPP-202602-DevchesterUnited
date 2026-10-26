package com.devchesterunited.devchesterunitedv2.persistence.auth.repository;

import com.devchesterunited.devchesterunitedv2.persistence.auth.entity.UsuarioEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Recibe identidades ya normalizadas por la capa de entrada. */
public interface UsuarioRepository extends JpaRepository<UsuarioEntity, UUID> {
    Optional<UsuarioEntity> findByNombreUsuario(String nombreUsuario);
    Optional<UsuarioEntity> findByCorreo(String correo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select usuario from UsuarioEntity usuario where usuario.id = :id")
    Optional<UsuarioEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("select usuario.id from UsuarioEntity usuario order by usuario.id")
    List<UUID> findIdsOrderById();

    @Query(value = "SELECT * FROM usuarios WHERE id = :id FOR UPDATE NOWAIT", nativeQuery = true)
    Optional<UsuarioEntity> findByIdForUpdateNoWait(@Param("id") UUID id);
}
